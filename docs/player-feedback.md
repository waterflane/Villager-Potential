# Optional player feedback

Villager Potential keeps player-facing aptitude feedback disabled by default.
Servers may configure `playerFeedback.aptitudeDisplay` in the server config:

- `DISABLED` sends no Villager Potential component and leaves villager
  interaction visually unchanged;
- `QUALITATIVE` shows the current profession's `Poor`, `Average`, `Promising`,
  `Talented`, or `Exceptional` tier in the vanilla action bar;
- `EXACT` is an explicit server-owner opt-in that also shows the stored
  aptitude number.

The tier boundaries follow the configured aptitude mean, variance, generation
bounds, and rare-talent strength. Feedback is emitted only for the villager's
current non-nitwit profession and does not include specialization internals,
learned palette data, Trade Memory, demand, TradeKeys, or reroll terminology.

The interaction is not cancelled or replaced, and there is no custom screen.
The server sends ordinary localized text components. Common code contains no
Minecraft client references; the build rejects client-only imports outside the
reserved client package so dedicated-server loading remains protected.
