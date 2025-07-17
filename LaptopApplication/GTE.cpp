#include <winsock2.h>
#include <windows.h>
#include <iostream>
#include <string>
#include <sstream>
#include <vector>

#pragma comment(lib, "ws2_32.lib")

#define ANDROID_PORT 7000
#define PC_PORT 7001

const int TABLET_WIDTH = 2560;
const int TABLET_HEIGHT = 1600;
const float PRESS_THRESHOLD = 0.1f;

int screenWidth, screenHeight;

void moveMouse(int x, int y) {
    SetCursorPos(x, y);
}

void mouseDown() {
    mouse_event(MOUSEEVENTF_LEFTDOWN, 0, 0, 0, 0);
}

void mouseUp() {
    mouse_event(MOUSEEVENTF_LEFTUP, 0, 0, 0, 0);
}

std::vector<std::string> split(const std::string& str, char delimiter) {
    std::stringstream ss(str);
    std::string item;
    std::vector<std::string> elems;
    while (getline(ss, item, delimiter)) {
        elems.push_back(item);
    }
    return elems;
}

void setupAdbReverse() {
    std::string adbPath = "C:\\platform-tools\\adb.exe"; // Adjust path if needed
    std::string command = "\"" + adbPath + "\" reverse tcp:7000 tcp:7001";
    int result = system(command.c_str());

    if (result == 0) {
        std::cout << "ADB reverse port forwarding set up successfully.\n";
    } else {
        std::cerr << "ADB reverse failed (code " << result << ").\n";
    }
}


int main() {
    setupAdbReverse();

    GetSystemMetrics(SM_CXSCREEN); // Trigger system initialization
    screenWidth = GetSystemMetrics(SM_CXSCREEN);
    screenHeight = GetSystemMetrics(SM_CYSCREEN);

    WSADATA wsaData;
    WSAStartup(MAKEWORD(2, 2), &wsaData);

    SOCKET serverSocket = socket(AF_INET, SOCK_STREAM, 0);
    sockaddr_in serverAddr{};
    serverAddr.sin_family = AF_INET;
    serverAddr.sin_addr.s_addr = inet_addr("127.0.0.1");
    serverAddr.sin_port = htons(PC_PORT);

    bind(serverSocket, (sockaddr*)&serverAddr, sizeof(serverAddr));
    listen(serverSocket, 1);
    std::cout << "Waiting for tablet connection on port " << PC_PORT << "..." << std::endl;

    SOCKET clientSocket = accept(serverSocket, nullptr, nullptr);
    std::cout << "Tablet connected!" << std::endl;

    char buffer[1024];
    std::string leftover;
    bool mousePressed = false;

    while (true) {
        int bytesReceived = recv(clientSocket, buffer, sizeof(buffer) - 1, 0);
        if (bytesReceived <= 0) break;

        buffer[bytesReceived] = '\0';
        std::string data = leftover + std::string(buffer);
        size_t pos;

        while ((pos = data.find('\n')) != std::string::npos) {
            std::string line = data.substr(0, pos);
            data.erase(0, pos + 1);

            auto parts = split(line, ',');
            if (parts.size() != 3) continue;

            float x = std::stof(parts[0]);
            float y = std::stof(parts[1]);
            std::string action = parts[2];

            int mappedX = int(x / TABLET_WIDTH * screenWidth);
            int mappedY = int(y / TABLET_HEIGHT * screenHeight);
            moveMouse(mappedX, mappedY);

            if (action == "hover") {
                if (mousePressed) {
                    mouseUp();
                    mousePressed = false;
                }
            } else {
                float pressure = std::stof(action);
                if (pressure > PRESS_THRESHOLD && !mousePressed) {
                    mouseDown();
                    mousePressed = true;
                } else if (pressure <= PRESS_THRESHOLD && mousePressed) {
                    mouseUp();
                    mousePressed = false;
                }
            }
        }

        leftover = data; // Keep partial data
    }

    closesocket(clientSocket);
    closesocket(serverSocket);
    WSACleanup();
    return 0;
}