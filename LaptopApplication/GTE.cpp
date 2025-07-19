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

// Pointer input injection typedef
typedef BOOL (WINAPI* InjectSyntheticPointerInput_t)(
    HSYNTHETICPOINTERDEVICE, const POINTER_TYPE_INFO*, UINT32
);

InjectSyntheticPointerInput_t InjectSyntheticPointerInputFunc = nullptr;
HSYNTHETICPOINTERDEVICE hPenPointer = nullptr;

void initPenInjection() {
    HMODULE user32 = GetModuleHandleA("user32.dll");
    InjectSyntheticPointerInputFunc = (InjectSyntheticPointerInput_t)GetProcAddress(user32, "InjectSyntheticPointerInput");

    if (!InjectSyntheticPointerInputFunc) {
        std::cerr << "InjectSyntheticPointerInput not supported on this version of Windows.\n";
        exit(1);
    }

    hPenPointer = CreateSyntheticPointerDevice(PT_PEN, 1, POINTER_FEEDBACK_DEFAULT);
    if (!hPenPointer) {
        std::cerr << "Failed to create synthetic pen pointer.\n";
        exit(1);
    }
}

void injectPen(int x, int y, float pressure, bool isDown, bool isHovering) {
    POINTER_TYPE_INFO info = {};
    info.type = PT_PEN;

    POINTER_PEN_INFO penInfo = {};
    penInfo.pointerInfo.pointerType = PT_PEN;
    penInfo.pointerInfo.pointerId = 0;
    penInfo.pointerInfo.ptPixelLocation.x = x;
    penInfo.pointerInfo.ptPixelLocation.y = y;
    penInfo.pointerInfo.pointerFlags = POINTER_FLAG_INRANGE;

    if (isHovering) {
        penInfo.pointerInfo.pointerFlags |= POINTER_FLAG_UPDATE;
    } else if (isDown) {
        penInfo.pointerInfo.pointerFlags |= POINTER_FLAG_INCONTACT | POINTER_FLAG_DOWN;
    } else {
        penInfo.pointerInfo.pointerFlags |= POINTER_FLAG_UP;
    }

    penInfo.pressure = static_cast<UINT32>(pressure * 1024);
    penInfo.tiltX = 0;  // Tilt X in degrees
    penInfo.tiltY = 0;  // Tilt Y in degrees
    penInfo.penFlags = PEN_FLAG_NONE;
    penInfo.penMask = PEN_MASK_PRESSURE;

    info.penInfo = penInfo;

    InjectSyntheticPointerInputFunc(hPenPointer, &info, 1);
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

    // Get screen dimensions
    screenWidth = GetSystemMetrics(SM_CXSCREEN);
    screenHeight = GetSystemMetrics(SM_CYSCREEN);

    initPenInjection();

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
    bool isPenDown = false;

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

            if (action == "hover") {
                injectPen(mappedX, mappedY, 0.0f, false, true);
                isPenDown = false;
            } else {
                float pressure = std::stof(action);
                bool press = pressure > PRESS_THRESHOLD;
                injectPen(mappedX, mappedY, pressure, press, false);
                isPenDown = press;
            }
        }
        leftover = data;
    }

    closesocket(clientSocket);
    closesocket(serverSocket);
    WSACleanup();

    if (hPenPointer) {
        DestroySyntheticPointerDevice(hPenPointer);
    }

    return 0;
}
/* Compile command for Windows:

cd %USERPROFILE%\OneDrive\Desktop\Graphics-Tablet-Emulator
cl .\LaptopApplication\GTE.cpp /Fe:.\Executables\GTE.exe user32.lib ws2_32.lib

*/