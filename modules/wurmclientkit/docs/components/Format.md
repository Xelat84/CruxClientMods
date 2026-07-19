# `Format`

`com.wurmonline.clientkit.Format` — display-string helpers that render numbers the way the Wurm
client does. **Pure logic, zero client dependencies** — it's in the `clientkit` package and could run
in a unit test. `final`, all-static, private constructor.

## Methods

### `coin(long iron) → String`

Formats an iron amount as a short coin string using the **largest two units**. Wurm's coin base unit
is iron; 1 copper = 100 iron, 1 silver = 100 copper, 1 gold = 100 silver.

| Input (iron) | Output |
|--------------|--------|
| `0` (or ≤0) | `"0i"` |
| `602` | `"6c 2i"` |
| `18000` | `"1s 80c"` |

Only the two most-significant non-zero units are shown, so a huge amount reads as `"3g 40s"` rather
than a four-part string. Use it for prices, balances, escrow.

### `weight(float grams) → String`

Grams → kilograms with two decimals: `1500f → "1.50 kg"`, `1000f → "1.00 kg"`. Wurm stores weights as
integer grams (`weight/1000 = kg`); pass the gram value.

### `duration(long remainMs) → String`

A **relative remaining** duration in milliseconds → a compact largest-two-units string:

| Input | Output |
|-------|--------|
| `≤ 0` | `"expired"` |
| ~2 days 4h | `"2d 04h"` |
| ~5h 12m | `"5h 12m"` |
| ~30m | `"30m"` |

Days show hours, hours show minutes, sub-hour shows just minutes. Good for auction time-left,
cooldowns, timers.

### `timeLeft(long expiryEpochMs) → String`

Convenience over `duration`: takes an **absolute** epoch-ms expiry and returns the remaining duration
(`duration(expiryEpochMs - System.currentTimeMillis())`). Use when the server sends you an absolute
expiry timestamp rather than a countdown.

### `ql(double q) → String`

Quality formatting: whole numbers show no decimals, otherwise two decimals. `92.0 → "92"`,
`92.34 → "92.34"`. Keeps QL columns tidy without a trailing `.00`.

## Usage

```java
row.setColumn(1, Format.coin(priceIron));      // "1s 80c"
row.setColumn(2, Format.weight(grams));        // "1.50 kg"
row.setColumn(3, Format.ql(quality));          // "92"
label.setLabel(Format.timeLeft(expiryEpochMs));// "2d 04h"
```

## Notes & gotchas

- **`coin` truncates to two units** by design — it's for compact display, not exact accounting. If you
  need every unit, format it yourself.
- **`timeLeft` reads the wall clock** (`System.currentTimeMillis()`), so client/server clock skew
  shifts it. For anything precise, have the server send a countdown and use `duration`.
- **`weight` and `ql` are locale-sensitive** — they use `String.format("%.2f", …)`, whose decimal
  separator follows the default locale (a comma-decimal locale renders `1,50 kg`). If a mod must
  guarantee a `.` separator regardless of
  system locale, that's a known limitation to address if it ever bites (none of the current mods run
  in a non-`.` locale).

## Source

`src/main/java/com/wurmonline/clientkit/Format.java`
