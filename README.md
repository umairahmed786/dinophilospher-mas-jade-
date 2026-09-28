# Dining Philosophers — Multi-Agent System (JADE)

A JADE-based multi-agent simulation of the classic **Dining Philosophers** problem. Philosophers sit around a shared table, think, pick up forks, eat, and talk to neighbours when a fork is busy. They can also pair up for **joint thinking** between meals.

This project is part of *Foundations of Agentic AI* (Semester 1).

---

## Problem

Five philosophers sit at a round table. Between each pair of seats there is one fork, so there are as many forks as philosophers. A philosopher needs **both** the left and the right fork to eat. If everyone grabs one fork and waits forever for the other, the system deadlocks.

This implementation treats the table and each philosopher as **JADE agents** that coordinate with FIPA ACL messages instead of shared locks in a single process.

---

## Architecture

```
                    ┌─────────────┐
                    │    table    │  owns the fork array
                    └──────┬──────┘
           CFP / INFORM    │     AGREE / REFUSE
                           │
     ┌──────────┬──────────┼──────────┬──────────┐
     ▼          ▼          ▼          ▼          ▼
  phil0      phil1      phil2      phil3      phil4
     │          │          │          │          │
     └──────────┴──────────┴──────────┴──────────┘
        REQUEST (release fork) · PROPOSE / ACCEPT / CONFIRM / REJECT
                         (joint thinking)
```

| Agent | Local name | Role |
| --- | --- | --- |
| `Table` | `table` | Holds one boolean per fork. Grants or refuses pickup. Marks a fork free on release. |
| `DPAgent` | `phil0` … `phil4` | One philosopher per seat. Thinks, requests forks, eats, drops forks, negotiates with peers. |

`Main` creates a JADE main container on **localhost:8888**, starts the table, then starts the philosophers.

Default seating: **5** philosophers (`number_of_philosophers` in `Main.java`).

---

## Project layout

```
dinophilospher-mas-jade/
├── Main.java                          # Bootstraps the JADE container and agents
├── src/diningphilosopher/
│   ├── Table.java                     # Fork resource manager
│   └── DPAgent.java                   # Philosopher life cycle and peer protocols
└── README.md
```

---

## How it works

### Table (`Table.java`)

The table stores `forks[i]`: `true` means free, `false` means taken.

For philosopher `k` (agent name `philk`):

- **Right fork** index = `k`
- **Left fork** index = `(k + N − 1) mod N`

Incoming messages:

| Performative | Content | Meaning |
| --- | --- | --- |
| `CFP` | `"left"` or `"right"` | Request that fork. Reply `AGREE` and mark it taken, or `REFUSE`. |
| `INFORM` | `"left"` or `"right"` | Release that fork. Mark it free. |

The sender’s seat is parsed from the local name (`phil3` → `3`).

### Philosopher (`DPAgent.java`)

Each philosopher is given:

- `position` — seat index
- `numberOfPhilosophers` — table size `N`
- neighbours `phil((i−1) mod N)` (left) and `phil((i+1) mod N)` (right)

Internal state includes hunger (`0` … `maxHunger`, default **5**), whether the left/right fork is held, whether the agent is eating, pending table replies, and optional joint-thinking partner.

A `CyclicBehaviour` (`LifeCycle`) either handles an ACL message or, if the inbox is empty, runs `deliberateAndAct()` and then blocks for **200 ms**.

#### Life cycle (when not waiting on the table)

1. If eating and `hunger > 0` → keep eating, decrease hunger.
2. If eating and still holding the left fork → drop left, then broadcast joint-thinking proposals.
3. If eating and still holding the right fork → stop eating, drop right.
4. If `hunger < maxHunger` → think (hunger increases).
5. If left fork missing → request left from the table; break any joint-thinking pair.
6. If right fork missing → request right from the table.
7. Otherwise both forks are held → start eating.

Forks are taken **left then right**. They are dropped **left then right** after the meal.

#### When the table refuses a fork

The philosopher asks the neighbour who currently holds that shared fork to give it up:

- Left refused → `REQUEST` `"release-right"` to the left neighbour.
- Right refused → `REQUEST` `"release-left"` to the right neighbour.

A neighbour **agrees** only if they hold that fork and are **not eating**. Otherwise they `REFUSE` with `"busy"`.

#### Joint thinking

After dropping the left fork (meal winding down), a philosopher sends `PROPOSE` `"lets-tink-together"` to every other philosopher.

Handshake (simplified):

1. Idle, hungry-enough, unpaired agent → `ACCEPT_PROPOSAL` and reserves the sender.
2. Proposer → may `CONFIRM` and enter joint thinking.
3. Either side can `REJECT_PROPOSAL` (busy, already paired, or ending the pair).
4. When someone needs the left fork to eat, they send `REJECT_PROPOSAL` `"joint-thinking-stop"` to the partner.

While paired, console output is `philX is joint thinking with philY` instead of `philX is thinking.`

---

## ACL messages (summary)

| From | To | Performative | Typical content |
| --- | --- | --- | --- |
| Philosopher | Table | `CFP` | `left` / `right` |
| Table | Philosopher | `AGREE` / `REFUSE` | fork index |
| Philosopher | Table | `INFORM` | `left` / `right` (drop) |
| Philosopher | Neighbour | `REQUEST` | `release-left` / `release-right` |
| Neighbour | Philosopher | `AGREE` / `REFUSE` | `released` / `busy` |
| Philosopher | Peers | `PROPOSE` | `lets-tink-together` |
| Peer | Philosopher | `ACCEPT_PROPOSAL` / `REJECT_PROPOSAL` / `CONFIRM` | handshake / stop |

---

## Requirements

- **JDK 8+** (any Java version that can compile against JADE)
- **[JADE](https://jade.tilab.com/)** on the classpath (`jade.jar`)

There is no Maven/Gradle build file in this repo. Point `javac` / `java` at JADE yourself.

---

## Build and run

Place `jade.jar` in the project (or use an absolute path). From the project root:

```bash
# Compile (sources live in the root and under src/)
javac -cp "jade.jar:src:." Main.java src/diningphilosopher/*.java

# Run
java -cp "jade.jar:src:." Main
```

On Windows, use `;` instead of `:` in the classpath:

```bat
javac -cp "jade.jar;src;." Main.java src\diningphilosopher\*.java
java -cp "jade.jar;src;." Main
```

JADE starts a main container on **port 8888**. Keep that port free.

You should see lines such as:

```
Table agent initialized
phil0 is thinking.
phil1 is thinking.
phil2 got LEFT fork.
phil2 got RIGHT fork.
phil2 is eating.
phil2 dropped LEFT fork.
phil0 is joint thinking with phil3
```

Stop the process with `Ctrl+C`. `Runtime.setCloseVM(true)` shuts down the JVM when the container closes.

---

## Configuration

| What | Where |
| --- | --- |
| Number of philosophers | `number_of_philosophers` in `Main.java` (default `5`) |
| Hunger before eating | `DPAgent.maxHunger` (default `5`) |
| Think / act tick | `block(200)` in `LifeCycle` (milliseconds) |
| Container host / port | `new ProfileImpl("localhost", 8888, null)` in `Main.java` |

If you change `N`, fork indices and neighbour names stay consistent: seats are always `phil0` … `phil(N-1)` on a ring.

---

## Design notes

- **Resource agent:** forks are not fields on philosophers; the table is the only owner of availability.
- **Asynchronous pickup:** a philosopher waits (`waitingLeftReply` / `waitingRightReply`) until the table answers before asking for the other fork or acting again.
- **Courtesy protocol:** a refused CFP does not spin on the table; the holder is asked to release if they are not eating.
- **Cooperation beyond forks:** joint thinking is a separate FIPA-style propose/accept/confirm dialogue among peers.

This is a teaching MAS, not a formal deadlock-free proof. With courtesy releases and staggered ticks, the run usually makes progress, but you can still observe contention in the logs.

---

## Course context

**Foundations of Agentic AI** — dining philosophers as a multi-agent system using JADE containers, cyclic behaviours, and ACL performatives (`CFP`, `AGREE`, `REFUSE`, `INFORM`, `REQUEST`, `PROPOSE`, `ACCEPT_PROPOSAL`, `CONFIRM`, `REJECT_PROPOSAL`).
