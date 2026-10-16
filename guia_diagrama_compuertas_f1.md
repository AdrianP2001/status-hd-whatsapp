# Guía de Implementación: Función $F_1$

**Función asignada ($F_1$):**
$$F_1 = (A \cdot B \cdot C) + (A \cdot B \cdot D) + (A \cdot C \cdot D) + (B \cdot C \cdot D)$$

---

## 1. Diagrama Esquemático de $F_1$

![Esquema Eléctrico del Circuito Booleano F1](C:/Users/adrip/.gemini/antigravity-ide/brain/b7089a10-576a-464f-9fee-f0d0e12ad99c/circuito_compuertas_f1_1792186023610.jpg)

> [!NOTE]
> Como el integrado **74LS08** tiene compuertas AND de solo **2 entradas**, cada término de 3 entradas se resuelve asociando de dos en dos:
> * $T_1 = (A \cdot B) \cdot C$
> * $T_2 = (A \cdot B) \cdot D$
> * $T_3 = (C \cdot D) \cdot A$
> * $T_4 = (C \cdot D) \cdot B$
>
> Para los 4 términos se usan **6 compuertas AND** (se requieren **2 integrados 74LS08**) y **3 compuertas OR** (se requiere **1 integrado 74LS32**).

---

## 2. Mapa de Conexión Física (Paso a Paso en Protoboard / Tinkercad)

Se usan 3 integrados:
* **U1:** Primer 74LS08 (Quad 2-Input AND)
* **U2:** Segundo 74LS08 (Quad 2-Input AND)
* **U3:** 74LS32 (Quad 2-Input OR)

### Alimentación de los C.I. (Obligatorio en la rúbrica):
* **Pin 14:** Al riel $+5\,\text{V}$ en U1, U2 y U3.
* **Pin 7:** Al riel $\text{GND}$ en U1, U2 y U3.

---

### Mapeo de Pines de Compuertas:

#### U1: Primer 74LS08 (Genera productos base y términos $T_1$ y $T_2$)
| Compuerta | Entrada 1 | Entrada 2 | Salida | Resultado |
| :--- | :--- | :--- | :--- | :--- |
| **Compuerta 1** | Pin 1 ($A$) | Pin 2 ($B$) | **Pin 3** | $P_{AB} = A \cdot B$ |
| **Compuerta 2** | Pin 4 (desde Pin 3 $P_{AB}$) | Pin 5 ($C$) | **Pin 6** | **$T_1 = A \cdot B \cdot C$** |
| **Compuerta 3** | Pin 9 (desde Pin 3 $P_{AB}$) | Pin 10 ($D$) | **Pin 8** | **$T_2 = A \cdot B \cdot D$** |
| **Compuerta 4** | Pin 12 ($C$) | Pin 13 ($D$) | **Pin 11** | $P_{CD} = C \cdot D$ |

#### U2: Segundo 74LS08 (Genera términos $T_3$ y $T_4$)
| Compuerta | Entrada 1 | Entrada 2 | Salida | Resultado |
| :--- | :--- | :--- | :--- | :--- |
| **Compuerta 1** | Pin 1 (desde U1 Pin 11 $P_{CD}$) | Pin 2 ($A$) | **Pin 3** | **$T_3 = A \cdot C \cdot D$** |
| **Compuerta 2** | Pin 4 (desde U1 Pin 11 $P_{CD}$) | Pin 5 ($B$) | **Pin 6** | **$T_4 = B \cdot C \cdot D$** |

#### U3: 74LS32 (Suma los 4 términos OR)
| Compuerta | Entrada 1 | Entrada 2 | Salida | Resultado |
| :--- | :--- | :--- | :--- | :--- |
| **OR 1** | Pin 1 (U1 Pin 6 $T_1$) | Pin 2 (U1 Pin 8 $T_2$) | **Pin 3** | $O_1 = T_1 + T_2$ |
| **OR 2** | Pin 4 (U2 Pin 3 $T_3$) | Pin 5 (U2 Pin 6 $T_4$) | **Pin 6** | $O_2 = T_3 + T_4$ |
| **OR 3 (Final)** | Pin 9 (desde Pin 3 $O_1$) | Pin 10 (desde Pin 6 $O_2$) | **Pin 8** | **$F_1$ (Al LED de salida)** |

---

## 3. Conexión del Indicador LED
* **Ánodo (pata larga):** Conectado al **Pin 8 del 74LS32**.
* **Cátodo (pata corta):** Conectado a una resistencia de $330\,\Omega$.
* **Extremo opuesto de la resistencia:** A $\text{GND}$.

---

## 4. Tabla de Verdad Resultante de $F_1$ (Función Mayoría de 4 variables)
La salida es $1$ únicamente cuando **3 o más entradas son $1$**:

| Fila | $A$ | $B$ | $C$ | $D$ | $T_1 (ABC)$ | $T_2 (ABD)$ | $T_3 (ACD)$ | $T_4 (BCD)$ | **Salida $F_1$ (LED)** |
|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| **0** | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | **APAGADO (0)** |
| **1** | 0 | 0 | 0 | 1 | 0 | 0 | 0 | 0 | **APAGADO (0)** |
| **2** | 0 | 0 | 1 | 0 | 0 | 0 | 0 | 0 | **APAGADO (0)** |
| **3** | 0 | 0 | 1 | 1 | 0 | 0 | 0 | 0 | **APAGADO (0)** |
| **4** | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | **APAGADO (0)** |
| **5** | 0 | 1 | 0 | 1 | 0 | 0 | 0 | 0 | **APAGADO (0)** |
| **6** | 0 | 1 | 1 | 0 | 0 | 0 | 0 | 0 | **APAGADO (0)** |
| **7** | 0 | 1 | 1 | 1 | 0 | 0 | 0 | 1 | **ENCENDIDO (1)** |
| **8** | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | **APAGADO (0)** |
| **9** | 1 | 0 | 0 | 1 | 0 | 0 | 0 | 0 | **APAGADO (0)** |
| **10** | 1 | 0 | 1 | 0 | 0 | 0 | 0 | 0 | **APAGADO (0)** |
| **11** | 1 | 0 | 1 | 1 | 0 | 0 | 1 | 0 | **ENCENDIDO (1)** |
| **12** | 1 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | **APAGADO (0)** |
| **13** | 1 | 1 | 0 | 1 | 0 | 1 | 0 | 0 | **ENCENDIDO (1)** |
| **14** | 1 | 1 | 1 | 0 | 1 | 0 | 0 | 0 | **ENCENDIDO (1)** |
| **15** | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | **ENCENDIDO (1)** |
