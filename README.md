# SmartHomeTwin 🏡

SmartHomeTwin is a software-only smart home simulation that allows users to control virtual appliances, monitor estimated power consumption, and receive a warning when combined power usage exceeds a configured threshold.

> **Project Type:** Academic Mini Project  
> **Technology Stack:** Java, HTML5, CSS3, JavaScript  
> **Backend:** Java built-in `HttpServer`  
> **Local Demo URL:** `http://localhost:8787`

## Features

- **User Authentication:** Demo signup, login, and logout functionality.
- **Virtual Appliance Control:** Switch simulated appliances ON or OFF.
- **Power Consumption Calculation:** Calculate the estimated wattage of all active appliances.
- **Power Threshold Warning:** Display a warning when total estimated power exceeds the configured limit.
- **Activity History:** Record device actions and safety-related events.
- **Interactive Dashboard:** View appliance states, estimated power usage, and safety status.

## Technology Stack

| Layer | Technology | Purpose |
|---|---|---|
| Backend | Java | Implements application logic, device state management, power calculation, and warning conditions |
| HTTP Server | Java built-in `HttpServer` | Handles HTTP requests and serves the web application |
| Frontend Structure | HTML5 | Defines the dashboard and authentication interface |
| Styling | CSS3 | Controls layout, colors, visual appearance, and responsive styling |
| Client-Side Logic | JavaScript | Handles user interactions and communicates with the backend |
| API Communication | HTTP endpoints | Transfers requests and responses between the frontend and Java server |
| Authentication | Java-based demo authentication | Supports the project's signup, login, and logout flows |
| Local Data Storage | Local files | Stores demo user data and activity history, as implemented |
| Version Control | Git | Tracks source-code changes |
| Repository Hosting | GitHub | Stores and shares the project source code |
| Development Environment | Visual Studio Code | Used for coding, testing, and project management |

### Application Architecture

SmartHomeTwin follows a simple client-server architecture.

1. The user interacts with the web interface through a browser.
2. JavaScript sends HTTP requests to the Java backend.
3. The Java server processes appliance-control and power-calculation requests.
4. The server returns the updated application state and safety information.
5. The frontend displays the current device states, estimated power, and warning status.

## How It Works

1. The user opens the web application and signs in to the demo.
2. The dashboard requests the current virtual-device state from the Java server.
3. When the user toggles an appliance, the server updates its simulated state.
4. The server calculates the total estimated power consumed by appliances that are ON.
5. The calculated total is compared with the configured power threshold.
6. The dashboard displays the latest state and any applicable warning.

## Simulated Appliances

The following example power values have been used in the project configuration. Verify them against the current source code before changing the configuration.

| Appliance | Example Power |
|---|---:|
| Light | 12 W |
| Fan | 60 W |
| Air Conditioner (AC) | 1500 W |
| TV | 100 W |
| Kitchen Light | 10 W |
| Refrigerator | 180 W |

The configured warning threshold is **1500 W**. A warning is displayed when the estimated total power is greater than this threshold.

These are predefined simulation values, not real-time measurements from physical appliances.

## Requirements

- Java JDK installed
- `javac` and `java` available in the terminal
- A modern web browser
- Visual Studio Code or another code editor
- Project source files, including `SmartHomeTwinServer.java`, `index.html`, `styles.css`, and `app.js`

## Run Locally

Open the project directory containing `SmartHomeTwinServer.java` in the terminal.

### 1. Compile the Java server

```bash
mkdir -p out
javac -encoding UTF-8 -d out SmartHomeTwinServer.java
```

### 2. Start the server

```bash
java -cp out SmartHomeTwinServer
```

### 3. Open the application

Open the following URL in your browser:

```text
http://localhost:8787
```

Keep the terminal running while using the application. To stop the server, press `Control + C`.

## Example Test Cases

The following are expected example results based on the documented configuration. Verify them against the current implementation during testing.

| Test Case | Action | Expected Result |
|---|---|---|
| TC-01 | Keep all devices OFF | Estimated power is 0 W; no power warning |
| TC-02 | Turn AC ON | Estimated power is 1500 W; warning is not triggered if the condition uses `>` |
| TC-03 | Turn AC and fan ON | Estimated power is 1560 W; warning appears |
| TC-04 | Turn all devices ON | Estimated power is 1862 W; warning appears |

## Academic Concept Mapping

SmartHomeTwin can be used to demonstrate concepts from three computer science subjects.

### COA — Computer Organization and Architecture

**Selected Topic: 1.9 — Logic Gates and Boolean Algebra**

Supporting Topic: **1.6 — Number Systems**

- Appliance states are represented using Boolean ON/OFF values.
- ON can be conceptually mapped to binary `1`, and OFF to binary `0`.
- Conditional logic determines whether a power-threshold warning should be displayed.
- The application demonstrates software-based Boolean decision-making, not a physical logic-gate circuit.

### DAA — Design and Analysis of Algorithms

**Selected Topic: 1.3 — Asymptotic Analysis and Amortized Complexity**

- The power-calculation algorithm scans the collection of virtual devices.
- If each device is examined once, the time complexity is **O(n)**, where `n` is the number of devices.
- The running total uses **O(1)** auxiliary space, excluding the stored device collection.
- Amortized complexity is a theoretical part of the selected topic and should not be described as implemented unless the source code supports that claim.

### FLAT — Formal Language and Automata Theory

**Selected Topic: 1.5 — Deterministic Finite Automata (DFA)**

Supporting Topic: **1.7 — Construction of DFAs**

- An appliance can be modelled conceptually using two states: `OFF` and `ON`.
- The input `TOGGLE` switches the appliance from one state to the other.
- The current implementation uses application state and toggle logic; it should not be described as a dedicated DFA engine unless one exists in the source code.

## Important Limitations

- SmartHomeTwin is a software simulation and is not connected to physical appliances, smart plugs, or power meters.
- Wattage values are predefined examples and do not represent live measurements.
- The power-threshold feature displays a warning; it does not automatically shut down appliances.
- Authentication and local file storage are intended for an academic demonstration, not as a production-ready security system.

## Repository Structure

The main repository structure is:

```text
S_70_SmartHomeTwin/
├── src/
│   └── smarthome/
│       └── SmartHomeTwin.java
├── SmartHomeTwinServer.java
├── index.html
├── styles.css
├── app.js
├── .gitignore
├── README.md
└── reports/
    ├── COA Report/
    ├── DAA Report/
    └── FLAT report/
```

The report folders may contain the corresponding subject PDFs. Their exact filenames should match the files present in the repository.

Compiled files and the `out/` directory are excluded from version control through `.gitignore`.

## Project Reports

The repository includes or will include three subject-specific academic reports:

- **COA Report:** Logic Gates and Boolean Algebra
- **DAA Report:** Asymptotic Analysis and Amortized Complexity
- **FLAT Report:** Deterministic Finite Automata

Each report documents the relevant academic concepts, their connection to SmartHomeTwin, implementation details, testing, and limitations.

## Author

**Vasu Saini**  
**Registration Number:** 2411021061052  
**University:** Alliance University, Bengaluru

## License

This project was developed for academic purposes. A separate license file can be added if the project is intended for open-source distribution or reuse under specific terms.
