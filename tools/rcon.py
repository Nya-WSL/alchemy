"""极简 RCON 客户端，用于对开发服务器做端到端冒烟验证。

Minecraft 的 RCON 服务端（RconClient / PktUtils）用 **小端序** 读写长度和字段，
所以这里全部用 struct 的 '<' 前缀。

用法：
    python tools/rcon.py "list" "time query gametime"
"""

from __future__ import annotations

import socket
import struct
import sys

SERVERDATA_AUTH = 3
SERVERDATA_EXECCOMMAND = 2
SERVERDATA_RESPONSE_VALUE = 0


def pack(req_id: int, req_type: int, body: str) -> bytes:
    payload = body.encode("utf-8") + b"\x00\x00"
    # 长度字段只算 id + type + payload
    return struct.pack("<iii", 8 + len(payload), req_id, req_type) + payload


def recv_exact(sock: socket.socket, count: int) -> bytes:
    buf = b""
    while len(buf) < count:
        chunk = sock.recv(count - len(buf))
        if not chunk:
            raise ConnectionError("connection closed by server")
        buf += chunk
    return buf


def read_packet(sock: socket.socket) -> tuple[int, int, str]:
    (length,) = struct.unpack("<i", recv_exact(sock, 4))
    if not 10 <= length <= 65536:
        raise ValueError(f"bad packet length {length}")
    data = recv_exact(sock, length)
    req_id, req_type = struct.unpack("<ii", data[:8])
    body = data[8:-2].decode("utf-8", errors="replace")
    return req_id, req_type, body


def execute(
    commands: list[str],
    host: str = "127.0.0.1",
    port: int = 25575,
    password: str = "alchemytest",
    echo: bool = False,
) -> list[str]:
    """依次执行命令，返回每条命令的响应文本。"""
    responses: list[str] = []

    with socket.create_connection((host, port), timeout=20) as sock:
        sock.settimeout(20)
        sock.sendall(pack(1, SERVERDATA_AUTH, password))
        req_id, _, _ = read_packet(sock)
        if req_id == -1:
            raise PermissionError("RCON authentication failed")

        req_id = 10
        for command in commands:
            sock.sendall(pack(req_id, SERVERDATA_EXECCOMMAND, command))
            _, _, body = read_packet(sock)
            responses.append(body)
            if echo:
                print(f">> {command}")
                print(body)
                print()
            req_id += 1

    return responses


if __name__ == "__main__":
    args = sys.argv[1:]
    if not args:
        print(__doc__)
        raise SystemExit(1)
    execute(args, echo=True)
