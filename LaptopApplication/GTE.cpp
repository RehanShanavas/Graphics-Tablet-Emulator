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
const float PRESS_THRESHOLD = 0.0f;

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

void injectPen(int x, int y, float pressure, float tiltX, float tiltY, bool isDown, bool isHovering) {
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
    penInfo.tiltX = tiltX;  // Tilt X in degrees
    penInfo.tiltY = tiltY;  // Tilt Y in degrees
    penInfo.penFlags = PEN_FLAG_NONE;
    penInfo.penMask = PEN_MASK_PRESSURE | PEN_MASK_TILT_X | PEN_MASK_TILT_Y;

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
    SetProcessDPIAware();  // Must be first!
    GetSystemMetrics(SM_CXSCREEN); // Trigger system initialization
    screenWidth = GetSystemMetrics(SM_CXSCREEN);
    screenHeight = GetSystemMetrics(SM_CYSCREEN);

    std::cout << "Screen dimensions: " << screenWidth << "x" << screenHeight << std::endl;

    initPenInjection();

    WSADATA wsaData;
    WSAStartup(MAKEWORD(2, 2), &wsaData); // Initialize Winsock

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
            if (parts.size() != 5) continue;

            float fractionX = std::stof(parts[0]);
            float fractionY = std::stof(parts[1]);
            float pressure = std::stof(parts[2]);
            float tiltRadians = std::stof(parts[3]);
            float orientation = std::stof(parts[4]);

            float tilt = tiltRadians * (180.0f / 3.1415f); // Convert radians to degrees
            float tiltX = tilt * cosf(orientation);
            float tiltY = tilt * sinf(orientation);

            int mappedX = int(fractionX * screenWidth);
            int mappedY = int(fractionY * screenHeight);

            if (pressure < 0.0f) {
                injectPen(mappedX, mappedY, 0.0f, tiltX, tiltY, false, true);
                isPenDown = false;
            } else {
                bool press = pressure > PRESS_THRESHOLD;
                injectPen(mappedX, mappedY, pressure, tiltX, tiltY, press, false);
                isPenDown = press;
            }

            // std::cout << "TiltX :" << tiltX << "TiltY :" << tiltY << std::endl;

        }
        // Store any leftover data for the next iteration
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