import socket
import threading
import time
import tkinter as tk
from tkinter import scrolledtext, Entry, Button, ttk, Label, Radiobutton, Checkbutton, StringVar, BooleanVar
import json
import os

class P2PChat:
    def __init__(self, root):
        self.root = root
        self.root.title("P2P Chat Test")
        self.root.geometry("900x600")

        self.notebook = ttk.Notebook(root)
        self.notebook.pack(fill=tk.BOTH, expand=True)

        self.chat_tab = ttk.Frame(self.notebook)
        self.settings_tab = ttk.Frame(self.notebook)

        self.notebook.add(self.chat_tab, text="Чат")
        self.notebook.add(self.settings_tab, text="Налаштування")

        # Chat tab
        self.text_area = scrolledtext.ScrolledText(self.chat_tab, wrap=tk.WORD, height=15)
        self.text_area.pack(pady=10, padx=10, fill=tk.BOTH, expand=True)

        self.online_label = tk.Label(self.chat_tab, text="Онлайн користувачі:")
        self.online_label.pack(anchor=tk.W)

        self.online_list = tk.Listbox(self.chat_tab, height=5)
        self.online_list.pack(fill=tk.X, padx=10, pady=5)

        input_frame = tk.Frame(self.chat_tab)
        input_frame.pack(fill=tk.X, padx=10, pady=10)

        self.entry = Entry(input_frame)
        self.entry.pack(side=tk.LEFT, fill=tk.X, expand=True)
        self.entry.bind("<Return>", self.send_message)

        self.send_button = Button(input_frame, text="Надіслати", command=self.send_message)
        self.send_button.pack(side=tk.RIGHT)

        # Settings tab
        Label(self.settings_tab, text="Ім'я:").grid(row=0, column=0, sticky=tk.W, padx=10, pady=5)
        self.name_entry = Entry(self.settings_tab)
        self.name_entry.grid(row=0, column=1, padx=10, pady=5)

        Label(self.settings_tab, text="Режим:").grid(row=1, column=0, sticky=tk.W, padx=10, pady=5)
        self.mode_var = StringVar(value="local")
        Radiobutton(self.settings_tab, text="Локальна мережа", variable=self.mode_var, value="local").grid(row=1, column=1, sticky=tk.W)
        Radiobutton(self.settings_tab, text="Сервер", variable=self.mode_var, value="server").grid(row=2, column=1, sticky=tk.W)

        Label(self.settings_tab, text="Адреса сервера:").grid(row=3, column=0, sticky=tk.W, padx=10, pady=5)
        self.server_entry = Entry(self.settings_tab)
        self.server_entry.grid(row=3, column=1, padx=10, pady=5)

        self.run_server_var = BooleanVar()
        Checkbutton(self.settings_tab, text="Запустити як сервер", variable=self.run_server_var).grid(row=4, column=0, columnspan=2, padx=10, pady=5)

        Button(self.settings_tab, text="Застосувати", command=self.apply_settings).grid(row=5, column=0, columnspan=2, pady=10)

        self.port = 8888
        self.relay_port = 9999
        self.my_name = ""
        self.mode = "local"
        self.server_address = "127.0.0.1"
        self.run_server = False
        self.online_users = {}
        self.relay_clients = []
        self.relay_socket = None
        self.tcp_socket = None
        self.udp_socket = None
        self.running = False

        self.load_settings()
        self.apply_settings()

    def apply_settings(self):
        self.my_name = self.name_entry.get() or f"ПК_{int(time.time()) % 100}"
        self.mode = self.mode_var.get()
        self.server_address = self.server_entry.get() or "127.0.0.1"
        self.run_server = self.run_server_var.get()

        self.save_settings()
        self.restart_network()

    def restart_network(self):
        self.running = False
        time.sleep(0.1)  # Wait for threads to stop
        self.online_users.clear()
        self.update_online()

        if self.tcp_socket:
            self.tcp_socket.close()
            self.tcp_socket = None
        if self.udp_socket:
            self.udp_socket.close()
            self.udp_socket = None
        if self.relay_socket:
            self.relay_socket.close()
            self.relay_socket = None
        self.relay_clients.clear()

        self.running = True

        if self.run_server:
            threading.Thread(target=self.relay_server, daemon=True).start()
            self.add_message("Сервер запущено на порту 9999")

        if self.mode == "local":
            self.tcp_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.tcp_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            self.tcp_socket.bind(('', self.port))
            self.tcp_socket.listen(5)

            self.udp_socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            self.udp_socket.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
            self.udp_socket.bind(('', self.port))

            threading.Thread(target=self.tcp_server, daemon=True).start()
            threading.Thread(target=self.udp_listener, daemon=True).start()
            threading.Thread(target=self.udp_beacon, daemon=True).start()
        elif self.mode == "server":
            self.connect_to_relay()

        self.add_message(f"Мій ім'я: {self.my_name}, режим: {self.mode}")

    def connect_to_relay(self):
        try:
            self.relay_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.relay_socket.connect((self.server_address, self.relay_port))
            threading.Thread(target=self.relay_receive, daemon=True).start()
        except:
            self.add_message("Не вдалося підключитися до сервера")

    def relay_receive(self):
        while self.running and self.relay_socket:
            try:
                data = self.relay_socket.recv(1024).decode()
                if data:
                    self.add_message(data)
            except:
                break

    def relay_server(self):
        server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        server.bind(('', self.relay_port))
        server.listen(5)
        while self.running:
            try:
                client, addr = server.accept()
                self.relay_clients.append(client)
                threading.Thread(target=self.handle_relay_client, args=(client,), daemon=True).start()
            except:
                break

    def handle_relay_client(self, client):
        while self.running:
            try:
                data = client.recv(1024)
                if not data:
                    break
                message = f"{self.my_name}: {data.decode()}"
                self.add_message(message)
                for c in self.relay_clients:
                    if c != client:
                        try:
                            c.send(message.encode())
                        except:
                            pass
            except:
                break
        self.relay_clients.remove(client)
        client.close()

    def add_message(self, msg):
        def update():
            self.text_area.insert(tk.END, msg + '\n')
            self.text_area.see(tk.END)
        self.root.after(0, update)

    def send_message(self, event=None):
        msg = self.entry.get().strip()
        if msg:
            self.entry.delete(0, tk.END)
            full_msg = f"{self.my_name}: {msg}"
            self.add_message(f"Я: {msg}")
            for ip in list(self.online_users.keys()):
                threading.Thread(target=self.send_to_ip, args=(ip, msg), daemon=True).start()

    def send_to_ip(self, ip, msg):
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            s.settimeout(0.5)
            s.connect((ip, self.port))
            s.sendall(msg.encode())
            s.close()
        except:
            pass

    def tcp_server(self):
        while self.running:
            try:
                client, addr = self.tcp_socket.accept()
                threading.Thread(target=self.handle_client, args=(client, addr), daemon=True).start()
            except:
                break

    def handle_client(self, client, addr):
        try:
            data = client.recv(1024).decode()
            if data:
                sender = self.online_users.get(addr[0], "Невідомий")
                self.add_message(f"{sender}: {data}")
        except:
            pass
        finally:
            client.close()

    def udp_listener(self):
        while self.running:
            try:
                data, addr = self.udp_socket.recvfrom(1024)
                name = data.decode()
                self.online_users[addr[0]] = name
                self.update_online()
            except:
                break

    def udp_beacon(self):
        while self.running:
            try:
                self.udp_socket.sendto(self.my_name.encode(), ('<broadcast>', self.port))
                time.sleep(1)
            except:
                break

    def save_settings(self):
        settings = {
            'my_name': self.my_name,
            'mode': self.mode,
            'server_address': self.server_address,
            'run_server': self.run_server
        }
        try:
            with open('settings.json', 'w') as f:
                json.dump(settings, f)
        except:
            pass

    def load_settings(self):
        if os.path.exists('settings.json'):
            try:
                with open('settings.json', 'r') as f:
                    settings = json.load(f)
                self.my_name = settings.get('my_name', f"ПК_{int(time.time()) % 100}")
                self.mode = settings.get('mode', 'local')
                self.server_address = settings.get('server_address', '127.0.0.1')
                self.run_server = settings.get('run_server', False)
            except:
                pass
        # Set defaults if not loaded
        if not self.my_name:
            self.my_name = f"ПК_{int(time.time()) % 100}"
        self.name_entry.insert(0, self.my_name)
        self.mode_var.set(self.mode)
        self.server_entry.insert(0, self.server_address)
        self.run_server_var.set(self.run_server)

    def update_online(self):
        def update():
            self.online_list.delete(0, tk.END)
            for name in self.online_users.values():
                self.online_list.insert(tk.END, name)
        self.root.after(0, update)

if __name__ == "__main__":
    root = tk.Tk()
    app = P2PChat(root)
    root.mainloop()
