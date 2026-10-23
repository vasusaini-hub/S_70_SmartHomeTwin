# SmartHomeTwin 🏡

**SmartHomeTwin** is a software-only smart home simulation that lets
users control virtual appliances, monitor estimated power consumption,
and see a warning when the combined power usage crosses a configured
threshold.

> **Project type:** Academic mini project\
> **Stack:** Java, HTML, CSS, JavaScript\
> **Backend:** Java `HttpServer`\
> **Demo URL:** `http://localhost:8787`

## Features

-   **User authentication demo:** signup, login, and logout flows.
-   **Virtual appliance controls:** switch simulated devices ON or OFF.
-   **Power calculation:** calculate the total estimated wattage of
    devices that are ON.
-   **Power-threshold warning:** show a warning when total estimated
    power is above the configured limit.
-   **Activity history:** record device and safety-related events.
-   **Dashboard UI:** view device states, estimated power, and current
    safety status.

## Technology Stack

  Layer               Technology
  ------------------- -----------------------------------------------------
  Backend             Java
  HTTP server         Java built-in `HttpServer`
  Frontend            HTML, CSS, JavaScript
  Data storage        Local files for demo user data and activity history
  Local development   Java compiler and runtime

## How It Works

1.  The user opens the web interface and signs in to the local demo.
2.  The dashboard requests the current virtual-device state from the
    Java server.
3.  When a user toggles an appliance, the server updates its simulated
    state.
4.  The server adds the wattage of every ON device to calculate
    estimated power usage.
5.  The total is compared with the configured power limit.
6.  The dashboard displays the latest state and any power-threshold
    warning.

## Simulated Appliances

The demo configuration uses the following example power values. These
are **predefined simulation values**, not measurements from real
appliances.

  Appliance                Example power
  ---------------------- ---------------
  Light                             12 W
  Fan                               60 W
  Air conditioner (AC)            1500 W
  TV                               100 W
  Kitchen light                     10 W
  Refrigerator                     180 W

The configured power-warning threshold is **1500 W**. A warning is shown
when the estimated total is **greater than** the threshold.

## Requirements

-   Java JDK installed (`javac` and `java` available in the terminal)
-   A modern web browser
-   The project files: `SmartHomeTwinServer.java`, `index.html`,
    `styles.css`, and `app.js`

## Run Locally

Run these commands from the project directory containing
`SmartHomeTwinServer.java`:

``` bash
mkdir -p out
javac -encoding UTF-8 -d out SmartHomeTwinServer.java
java -cp out SmartHomeTwinServer
```

Then open this address in your browser:

``` text
http://localhost:8787
```

Keep the terminal running while using the application. To stop the
server, press `Control + C` in the terminal.

## Example Test Cases

  -----------------------------------------------------------------------
  Test                                Expected result
  ----------------------------------- -----------------------------------
  All devices OFF                     Estimated power is 0 W; no power
                                      warning

  AC ON                               Estimated power is 1500 W; no
                                      warning because the threshold is
                                      not exceeded

  AC + fan ON                         Estimated power is 1560 W; warning
                                      appears

  All devices ON                      Estimated power is 1862 W with the
                                      example values; warning appears
  -----------------------------------------------------------------------

## Academic Concept Mapping

The project can be used to discuss the following course concepts:

-   **COA --- Logic Gates and Boolean Algebra:** device ON/OFF states
    and Boolean threshold decisions. The application uses Java Boolean
    values; binary 0/1 is a conceptual mapping.
-   **DAA --- Asymptotic Analysis:** the total-power calculation scans
    the device collection once, so it is **O(n)** in the number of
    devices.
-   **FLAT --- Deterministic Finite Automata (DFA):** a virtual
    appliance can be modelled conceptually with `OFF` and `ON` states
    and a `TOGGLE` input. The current code uses Boolean state and toggle
    logic; it does not implement a separate DFA engine.

## Important Limitations

-   This is a **simulation**. It is not connected to physical
    appliances, smart plugs, or power meters.
-   Wattage values are predefined examples and should not be treated as
    live readings.
-   The power-threshold feature displays a warning; it does not
    automatically shut down devices.
-   The local authentication and file storage are intended for an
    academic demo, not as a production-ready security system.

## Repository Structure

The main files are expected to be arranged approximately like this:

``` text
SmartHomeTwin/
├── SmartHomeTwinServer.java
├── index.html
├── styles.css
├── app.js
├── out/                  # compiled Java classes (usually not committed)
├── reports/
│   ├── SmartHomeTwin_COA_Report.pdf
│   ├── SmartHomeTwin_DAA_Report.pdf
│   └── SmartHomeTwin_FLAT_Report.pdf
└── README.md
```

Adjust the structure in this section if your actual repository differs.

## Reports

Place the three subject reports in the `reports/` directory before
pushing, for example:

-   `reports/SmartHomeTwin_COA_Report.pdf`
-   `reports/SmartHomeTwin_DAA_Report.pdf`
-   `reports/SmartHomeTwin_FLAT_Report.pdf`

## Author

**Vasu Saini**\
Registration Number: `2411021061052`

## License

This project was developed for academic purposes. Add a license file if
you intend to publish or reuse it under a specific open-source license.
