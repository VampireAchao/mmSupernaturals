package me.matterz.supernaturals.it.harness;

/**
 * The skin every stand-in in the hall wears, frozen into the source.
 *
 * <p>A stand-in is a real {@code ServerPlayer} built around a hand-made profile
 * ({@link Nms#spawn(String, org.bukkit.World)}), and that profile carried no properties
 * at all - so every one of them drew as the default Steve or Alex, whatever race the
 * room was about. A skin is a {@code textures} property on that profile, and it has to
 * be on it <em>before</em> the player is placed in the world: the profile goes out with
 * the player-info packet that {@code placeNewPlayer} sends, and nothing sends it again.
 *
 * <p>Every value below is the real, Yggdrasil-signed texture of a real account, read once
 * by hand from {@code api.minecraftservices.com/minecraft/profile/lookup/name/<name>} and
 * then {@code sessionserver.mojang.com/session/minecraft/profile/<uuid>?unsigned=false},
 * and frozen here - so neither the integration tests nor the hand-run server touch the
 * network. Being signed they need no help from the server; the hall also sets
 * {@code enforce-secure-profile=false}, which is what an unsigned, hand-made skin would
 * need. Pick a replacement the same way: a profile whose {@code SKIN.url} is a player
 * texture. A mob texture put on a player draws as rubbish, which is why none of these
 * are the "MHF_" mob accounts - except the ones that are already player-shaped.
 *
 * <p>To change one, swap the two strings. Nothing else in the harness names a skin.
 */
final class Skins {

    /** One {@code textures} property: the base64 profile blob, and its signature. */
    record Skin(String value, String signature) {
    }

    /** vampire 厅的导览 —— 账号 Discra，NameMC tag/vampire #30（182 likes）；模型 slim。 */
    static final Skin VAMPIRE = new Skin(
            "ewogICJ0aW1lc3RhbXAiIDogMTc5MTk2MjE3NDQ2MCwKICAicHJvZmlsZUlkIiA6ICI3NzFiNGU4NWI4NTk0MzI0YjM3MjFk"
                    + "ZjM0ZTQ3NDhhMyIsCiAgInByb2ZpbGVOYW1lIiA6ICJEaXNjcmEiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAog"
                    + "ICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQu"
                    + "bmV0L3RleHR1cmUvYmJjNjYzYjdlMzY1YjI3Mzk3NWU1ZTZiYzdlMmE1NDI2MWIyODU2MWZjZDEyMGJjNGMwZTE0YmQyOTc0"
                    + "MTkxMiIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9",
            "slZlnQeGsEIykZSMXZ2Lifo/Ox95dfycOc3TQBYjdB1KxbfCSwxW6DFzuP5grqkPMyQD5K1h1sbmOJpkobn51vMToIslrvuM"
                    + "sGdVOgwTixzOXI/ZrIhMveUY96N575YXhqb82eyOlr2km0iIuas1gIfqMDtumblq3RyJSEP3Res772WFGoHCL9mo9F9PaS2V"
                    + "wCdl9Xaw35v/0BqBOl83HWN6rDQ4ex9gry56fcR7Z886Pz4AZ8jt8gsNnPcHKwWJOy9EmtFwzp/2oiuJblzDAg6DA40xLsne"
                    + "wLRLu6AXT1o/LExnD54/8kxyZDJCTzrfRRdcGXfMt791XvJDWFG9Zi+hx4bMktdCoS27ssqOHb/uLba3I8Kh1J4EnAZjTmXZ"
                    + "Xtkpp/g6g3mqygTtbtm0x/eOwASp33hKj2PYcCb2sDaTPY2hULWcbBNjQbeSIAL9Gc8h+95OxwMZY8RaCNA3KtDbLK7ZZOG8"
                    + "qIN3Zurywa5Njd4w5bBY9pH4mgYymbTRbEuzNn9imgitbN92uKy3CqRKxtaZ7BvYOMpuCIvgkBNs60Hp/gJPNXqZ+5H8YMiG"
                    + "V6jS1YoKbZ640+HaRaf9lv8h5H76cRIHw1yNxL2viY1cIdxkFuxar1uHj4B61HCV9fi+wG8PgU7XPsYPJfmNiKeIp8Mnb517"
                    + "2K2I2viPIfY=");

    /** ghoul 厅的导览 —— 账号 MHF_Herobrine，Mojang 固定皮肤账号（苍白人形）；模型 classic。 */
    static final Skin GHOUL = new Skin(
            "ewogICJ0aW1lc3RhbXAiIDogMTc5MTk2MjUxNTEyNiwKICAicHJvZmlsZUlkIiA6ICI5NTg2ZTVhYjE1N2E0NjU4YWQ4MGIw"
                    + "NzU1MmE5Y2E2MyIsCiAgInByb2ZpbGVOYW1lIiA6ICJNSEZfSGVyb2JyaW5lIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDog"
                    + "dHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWlu"
                    + "ZWNyYWZ0Lm5ldC90ZXh0dXJlLzJjNjVlZDI4MjljODNlMTE5YTgwZGZiMjIyMTY0NDNlODc4ZWYxMDY0OWM0YTM1NGY3NGJm"
                    + "NDVhZDA2YmMxYTciCiAgICB9CiAgfQp9",
            "skL/2UOm0dkkq1Qhr9XIjgHhagwg/uPU7GVAbjX3bKuQoEwLNxCjqwM2hQ11pcS648VedhpVyy/b9UKYPtBgWLi4+QF06MLf"
                    + "562kWfU+oxDdXtr+sXEmiF7WHJCD+n/M1MHn+Hp2mS5ItpSEPUYa2XDkDrn0XIcxq5yFcBTIw71cbwQDWx6cb7EJKFwar1ZE"
                    + "lDJcw45ZZM8rp8DO+XIFXDcJCAvkXla+5bRlxTNRcKXL1oE7C9y2vKhx2z49WVrfDmV9UMSFLmCS3Gyuv6Xo+adG0Qk35MsD"
                    + "13LFlNG1gsOqp7ap7dntsEZGHwE25q2OSHTwDVXSlZ+d19IHENCkmLBB6N+Dg+2XUGOOgr1+7Jvr7GVtBkbJTMhk+LKA6CuH"
                    + "USpmAb3VFld+lpIC3mp8ELb+9CbRGrzBPv+Tm+ocNl5tbBzQSu0FC1SjvBe800HisbsWoWMgMItRKtxg9b+SMgehFaMF94wz"
                    + "n0K9Po8NaQB1A/woUuuPPaEA14aI+DyPJulOtR9wIG5gbuNjKeJcRywfwxLvpeEZ/E6OwBIvwSWO/bh1MLHtrd/8do38rE3u"
                    + "uzXAkv/V0iEt5j/tg7kZ92ASXPV7MhudZgBjEGKP9yriDrN/9BiLu6nDauz0xzhruF7cgLCHvDfmdIwEFVrVBeUNfq48vcaF"
                    + "5FxtBW5MDb0=");

    /** werewolf 厅的导览 —— 账号 Chetney，NameMC tag/wolf #29（81 likes）；模型 slim。 */
    static final Skin WEREWOLF = new Skin(
            "ewogICJ0aW1lc3RhbXAiIDogMTc5MTk2MjMyMjI5MCwKICAicHJvZmlsZUlkIiA6ICI0Yjc3MWZlZTM1MjU0ZTNlYWQxMGVh"
                    + "Njk1NWI4ZTQ4MSIsCiAgInByb2ZpbGVOYW1lIiA6ICJDaGV0bmV5IiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwK"
                    + "ICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0"
                    + "Lm5ldC90ZXh0dXJlL2ZkZDFjYzAwMDdiODk4YWYzNmY3YTUyMjM1OGY1Mjg4MzFjMjhhNmEwMzRmY2I3MDMwYTk5ZmFjOTcy"
                    + "M2I0ZjciLAogICAgICAibWV0YWRhdGEiIDogewogICAgICAgICJtb2RlbCIgOiAic2xpbSIKICAgICAgfQogICAgfQogIH0K"
                    + "fQ==",
            "OaAE0HbIRHdoPUKcEkm7dMp+Dj2W6HrSdECwLt8ySWpabI+Klyzc1VGW3aizCMYEhiQTx70JwTZcAzzOc3z9Axdy5HpeBpFp"
                    + "6ReoEiQFk2CwJpr7tDCUel8NeS0HZE2QPtznvHbxCZBh+3du/ArRBcGJ9ZSO3Kw5GEVOWtIVieMovwTeviH0JAdeub0gBUcp"
                    + "w6HCpfGr2D3wBurxYEo1xnl1AFjqHLxVhO9PCi8CAoQjEJw1ojqavPSwKUIJlD6BfJNfUu2Q9ohDQy2jLl14Y4mIZu8nnf2t"
                    + "PRAxxccGD2+9//mVj0cdcGWr9XzvWbk0+WZ5vkLHw5jAHmnct8U9KbYTsOke4zRcrkM8dQHIkkSOP2Zjw/q31P5reJcg9pSv"
                    + "GAAINiyUKxxbq/owtev8yPXU28EQe8rh7YzUu6rIAUiFnHZ65xi9rSKyZ66TjjeAThIsGbSKDdheNdoEjCAKdy0fXTwqhYud"
                    + "Ld3+mvjeuCHMSidgJwNRDercvHlyyBu+pSP6NnoSb3ao0DpkvGKPlLq5knXBWKK/+uPnKvXGixbTnZYt1OY5+a9mesEaOIdK"
                    + "JHS74417OyFVGl1EA9QjU00Hs/iHrPR1Hiw021YQwtWGXcBSTVXX9540gu7QZtaW5kNE4Xv7hNbd5upKD2lytPJmBwfXylu5"
                    + "jf8fmeUegzM=");

    /** demon 厅（含下界两段走廊）的导览 —— 账号 gigakatze，NameMC tag/demon #16（4838 likes）；模型 slim。 */
    static final Skin DEMON = new Skin(
            "ewogICJ0aW1lc3RhbXAiIDogMTc5MTk2MjUxNzIzMiwKICAicHJvZmlsZUlkIiA6ICIxMzUwNmM4YzE0M2I0MDhiODY4YWNi"
                    + "OTFlZDgzNDgxZCIsCiAgInByb2ZpbGVOYW1lIiA6ICJnaWdha2F0emUiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVl"
                    + "LAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3Jh"
                    + "ZnQubmV0L3RleHR1cmUvYmUwMTlmOGE4YjU3NDk1YzNlM2ZjOTljOTI4MTVlNzk3NWYwNTAxYTIyOTUyZjZkZjMwOTY2NDkz"
                    + "NzFmMDExZCIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9LAog"
                    + "ICAgIkNBUEUiIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzJjNTc5"
                    + "OTY4YzY0YzE3MTk3NDBmZDhjMmE0NTE0NjE4NzliMjM4MDAyNTc0ZmNlNDhmN2QxYTdjMzZhMWM3ZDQiCiAgICB9CiAgfQp9",
            "phxO2x36zIWqBIrPStYhkPm8wH6QU/FFPZTAInVKYyUxgmCSg+D8Cmj7lGQzQIMfg+ol7pZq0WW16P7SGSVp1y843v0lkSjQ"
                    + "aYTPhGdI/LT3TWJejj2+8JnFCvoaQgA3mwJglXSKEW8rjs/utDcONVcOj3SIMkq86vHdNWOV0Bn8oGpex4vcHt7r4vN4sAEW"
                    + "0qUkqb9wGez/MK1QWLF0zOthpliMO8IRwxlHm+UwyHnHoKS8oF7Wnb7AEcAUUGtrz5NUoF0Oo8E2KlJ/5x2jxrvzFvapfAA3"
                    + "FIoXEkrNdmlmR8xT4XWotOMMVnWW/1Pi6NEAEbQuruKMfLNexd0R3GePTcX4w1vVy+2XRUnOD1AkGe2/HLRtTrU5yTO8IIqN"
                    + "URChgeDo4HEZD3UfLlbUXBP5eqV9DMGGJnCMmFGSKwLKnFRCAkOXZuIgpP4y+zq+czTyFGA/xQnvgfB223nnWI4PIMYAfhHP"
                    + "Fk56mXlq1H+1hf8ELkstdNZsmswNP/VnSQwdKzhuWm8Yjmc0gABrpFhxORJA0lvoQGJnrFYmIYU2CM5uv3shJNu+76evtRPf"
                    + "GsMQhB9fffoJvIZTZA83S9iBM+rsRh98V3PGi8FTxxaZ3dvLZYGS7E+DXKjfFuPmc69fQ8qgO1VuoILy/N5WHmUX6JQSnDy4"
                    + "UjwspsTu+U4=");

    /** enderborn 厅的导览 —— 账号 MHF_Enderman，Mojang 固定皮肤账号；模型 classic。 */
    static final Skin ENDERBORN = new Skin(
            "ewogICJ0aW1lc3RhbXAiIDogMTc5MTk2MjE4Njg3NiwKICAicHJvZmlsZUlkIiA6ICI0MGZmYjM3MjEyZjY0Njc4YjNmMjIx"
                    + "NzZiZjU2ZGQ0YiIsCiAgInByb2ZpbGVOYW1lIiA6ICJNSEZfRW5kZXJtYW4iLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0"
                    + "cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5l"
                    + "Y3JhZnQubmV0L3RleHR1cmUvMWIwOWEzNzUyNTEwZTkxNGIwYmRjOTA5NmIzOTJiYjM1OWY3YThlOGE5NTY2YTAyZTdmNjZm"
                    + "YWZmOGQ2Zjg5ZSIKICAgIH0KICB9Cn0=",
            "OYYduSdEFXQmOv6oITlizRIgdfdiBJ+Lgo2HlKAMqoBr58Kvg2GcS/d0CIu97sEThR4LfR7UQRudfGcqOht0ck4se2ljxDFX"
                    + "g/Hw+c+Ja84s3G38FNVQVQN6Fs64K7FI/GLCqZTelGbTQbaohZssMr0EefEJ54AGTqTbtfTC/bSp9XEb+IHXl1pk9tninZ/Y"
                    + "JoZESzLSXwd6RqcQsMTxi7EgUqN17AEVEjHJ5Bdj/a66waKcF5EjvSrNY3zvAkhdP1NpTwgSocK/qnaFBcBrSPQLfolpdqpW"
                    + "ZfYWWCI2cEvUn1ae0G3iJ321WXOx45c1Yyxjl6OQ2wUah1BvrGRzy21F4PSz6BwkPE1nNofXU+uMnPZdZcF0VB201qA1X7qE"
                    + "py+lB/qWMehlcGGth71TLjzZtGzfSnhyfURUla7SsanaJa7pxxAi6LxCTxiYqXsWJF4yt2cGRhMczFluwCyDuVhJUctg/dEM"
                    + "CTb8qF1zUdCcoWtZzerzR8HHPC/EKHkUgO7bK75PhwBEVYTCDaP8hrr6dBWyX6gExd0w1RRa/NjO5Je9+jZ5BdtxQONGQK9r"
                    + "s7g0VJ2fojJhykGoCjLTAQmAHTOgdwEJV4MXBSC7pj07G8AFazgA3j1Z09Iddl8Ve8OPmP/OBVwmhf6CffpqrP1L8lMzgX88"
                    + "/brUGej9C0o=");

    /** priest 厅的导览 —— 账号 snrl，NameMC tag/priest #13（1988 likes）；模型 slim。 */
    static final Skin PRIEST = new Skin(
            "ewogICJ0aW1lc3RhbXAiIDogMTc5MTk2MjE4ODkzNywKICAicHJvZmlsZUlkIiA6ICJmZmE2NGU2OWM5Mjk0MmJkYjU5YmFl"
                    + "NjIwMTYzZWQ5MiIsCiAgInByb2ZpbGVOYW1lIiA6ICJzbnJsIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAi"
                    + "dGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5l"
                    + "dC90ZXh0dXJlL2ZiNWIzMzZiNWIzNjdiNmRjMzM2NmJhNDhkOGJiZjQwZTM1ZTQ0MWNjY2UyMzI2OTcwZGViZDM2ZmIwZTYz"
                    + "ZSIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9",
            "PsX/v/zuukw39ZtQpWHFUuRo5KrlcIRvyvG+ctvW731LXje5aTHJvbOso9KwHPHAw7/UF/mS0sBUMRyOFqgs2AVjBJEPPIYY"
                    + "Q9+eXyg6WHyiItyf/83BMvMvjqs5E1wGMks6loTAVnQvUXQ9mDYXQ5h8KVMHD1VZgeSPzUMNWknFlShR4ghcWU1SW1sjUraX"
                    + "fYIanJUWceNF+vVifMTbCRVGx7Qant04ZccEBH+z3EQlne8Tv/qayLn2G9mz3pUjCG3nCZVRERphyW7OpXFB7oBFLaB2blDU"
                    + "lssoEhF4XPA9GD2Qv73MKVjeT5bwAbFCpt26F349vRtO/GyggN3sj669wgCNx6wXtE34Q+pKjGTOcPvuvG34tbp9NG3u4LKB"
                    + "WrWJQvVNny9mdbVC6E1pkbybIFqG/sxg+Z54wKP3t+1OSCwouanMktCEPmXyjg025etujiv206FoKhNI/wYSRKhA2oD1KQ7Q"
                    + "vSGwRmtj5QeBJf2o4Ew1u3gZD2LEIU1Y1nv3h5XWLWQ6ymIi+hs7Vpu9sjuAobxFC6GlNUDjhsewphlRFxtY8PvrvylmikNZ"
                    + "4XUw7alLYNUYj3vYJINRR8tLVXRU+asTvMp7xlq4UvnGUc4wlenyakTjJOa7IvrskIv9kEwV04EOmhtn7cmU6gsq7M/n9U6i"
                    + "ENkrWgn0H8U=");

    /** witchhunter 厅的导览 —— 账号 EPOS05，NameMC tag/hunter #14（244 likes）；模型 classic。 */
    static final Skin WITCHHUNTER = new Skin(
            "ewogICJ0aW1lc3RhbXAiIDogMTc5MTk2MjE5MTE2MSwKICAicHJvZmlsZUlkIiA6ICI5MDE4ODZmNmEwOGQ0NjBhYmVkMjll"
                    + "Mjc1YmMyZGFkMiIsCiAgInByb2ZpbGVOYW1lIiA6ICJFUE9TMDUiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAog"
                    + "ICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQu"
                    + "bmV0L3RleHR1cmUvN2E0OGVjZGQ2Y2I0NTU1ZjIxNzdkZThlMzU2YTI4MTJkOGQ5ZDVjN2EwM2QzMTdiZGMzNWZkODBlNTA3"
                    + "MzFmMSIKICAgIH0sCiAgICAiQ0FQRSIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0"
                    + "L3RleHR1cmUvMjRhYWZjNDUxYWEyY2MzNGRkYzcyNjUyMTE2Nzg1ODVjMGVmNGRhNGQzMmVkYjc1ZWNlYzFiZDhiNTQwODM4"
                    + "MSIKICAgIH0KICB9Cn0=",
            "to7Ww5+FVYXfbpq5/i4KITQEjvxaRDP/N3to+JI76pB3fU3Ay7onhO+yHy+GIqP0iIjpT000l0uHU62r4LrQlr3WR3Do22TA"
                    + "cgHOa02X/i3BraaomCBRSZ5vGQv1hkPzFZX5wAINRYvJtW6eoz0MJuXvXnr6u7pAdosa57SAAlYFhEpnSxJ7upC9yITvKf0L"
                    + "3LrZvQOKDxucWJpjX40+zdKPDSN6XR7gbVDWH7btetzDlLMwOvYYGpsjy95iMIV5LO0zoFWabCyo8LrCOzdlyw0cimD8rXT4"
                    + "iDcFHmaP2G9/gbxOp4DEXzvfKMynC9skIwW4ZpsD7xFfwQdNfZIGNW+uyS3nDHiutr1H1mV+YENL6EGzr8SNEHMPMXYJY7bP"
                    + "6GqVLJHEcU2YbyozNaP3ttWlOQD5Mucdba7d4t610SBuoaio/A7wrZZwFVU+8sncN/d71ARZ88oSzVrjhUhJ62ytVve7SdSG"
                    + "brTOuUOHR4ytoyh04p8quDQA77n+0NQu74jPFYpSTwxZnlNP10FGPcIAdQUhZtTMQfqb7AU8EStfCwmVrOP+2UHll+ogM6Mr"
                    + "xDYmJt4RrrBC9us08TzNRrB2d2JQYUaDgF55yG0PGdxtmb7s6bt8JA5+bsGeonoI1ClneZY/KCZKutlN6iFTFoTfUfU8eLiL"
                    + "xQ+98Nsjhek=");

    /** angel 厅的导览 —— 账号 Raydiance101，NameMC tag/angel #1（1944 likes）；模型 slim。 */
    static final Skin ANGEL = new Skin(
            "ewogICJ0aW1lc3RhbXAiIDogMTc5MTk2MjE5MzIwNywKICAicHJvZmlsZUlkIiA6ICJjNzQyYTliYzI0MTQ0ZmQ5OGQwNThj"
                    + "NmNkMTk3OTUwNyIsCiAgInByb2ZpbGVOYW1lIiA6ICJSYXlkaWFuY2UxMDEiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0"
                    + "cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5l"
                    + "Y3JhZnQubmV0L3RleHR1cmUvZDc3MDI1M2ZjYzFlNmQ3YmY4M2EwYWY0YzgzYjgwNDRjNmUxMTlmZTg5MGE5YzIyNmRhZTMy"
                    + "MTYyYWY0NDM3MSIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9"
                    + "CiAgfQp9",
            "i54SMXVHaTxLXUyzLY6z7IhuDHm5NkYyFPo/xus2/9U9USvExfcjHLSKV4BnWFZZdB1hY/YgRrdBnQjk1dTv+DPINGB1gKYa"
                    + "FOYBqi7OKLgk/a2Cvi79Tsprbk/NQljpFEOKGrOaFU+9ocThSHUSvBJdPnPBQ6pcYP/7NxPt8FKBmuuUDgymOgw1vpWfXZxC"
                    + "IAfZvwl2/sl2YjSjsm1FSMi32bSg3Bm83/3mcYPpBrUAItpOeNfzOuTj4oD2GyEfdICC/3yxVFbnmC8eZcHfuvrk75NjXdhU"
                    + "xPyPQZlxQCXzYUz8VUys7cRfSh4yIDiRket5JMsfXbQr4JEc0ciNZua0wUzuh7TPd2M+SuUUb97hyo7XjQJc4si4MojW0PVf"
                    + "4uo55QJkv9lgG4KM09zUU83/Pf9vpGBHXdoJFOKbijfsHkx2a71pmRbSDI/4X0uYzL4Fal/XaDxHDtdnufCKIAoWK04IXqpE"
                    + "Kh9I9PpCCv3CFJJL3olCtNN2LIUp7t4MSKdod0CJ9dQnkNS8/lk4u8oDbhBs1O8iyMt2ZfPHGvpoD2OW+pCuj2Kar07NWywQ"
                    + "A9Zy4gR5xDKQutC13WzxaBfdmjvzBfIkSwfWZ8TTNr0zZjJn5Rfhjc3/TmnR2nrlUwv1hBGhoLMUW0FEle/QFyLGU9ggUdbM"
                    + "K2r9DE8Vp9k=");

    /** 每个厅的「陪练教头」，以及陪练 SPAR 本人 —— 账号 Demacratic，NameMC tag/knight #1（3705 likes）；模型 slim。 */
    static final Skin MASTER = new Skin(
            "ewogICJ0aW1lc3RhbXAiIDogMTc5MTk2MjE5NTE2NCwKICAicHJvZmlsZUlkIiA6ICI4Y2EyZmFhYjJiMTg0YzU5YTUwNmFk"
                    + "OWUxMWJmNzhhZSIsCiAgInByb2ZpbGVOYW1lIiA6ICJEZW1hY3JhdGljIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1"
                    + "ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNy"
                    + "YWZ0Lm5ldC90ZXh0dXJlL2IyNThhM2EzZmFjYWUwYjIzNmRlMWFjYjc5MjEzNWM1ZmNjMjJiZDM2NDcxZTQwYjlhYWVmOTI2"
                    + "OTJkOWY0ZTQiLAogICAgICAibWV0YWRhdGEiIDogewogICAgICAgICJtb2RlbCIgOiAic2xpbSIKICAgICAgfQogICAgfSwK"
                    + "ICAgICJDQVBFIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS8yOGRl"
                    + "NGE4MTY4OGFkMThiNDllNzM1YTI3M2UwODZjMThmMWUzOTY2OTU2MTIzY2NiNTc0MDM0YzA2ZjVkMzM2IgogICAgfQogIH0K"
                    + "fQ==",
            "xKaHVKjHJQNyKDQpWA3n0yrhQNk1uojd6yyODGfIF00+pF1G/rqR//nKHuUuERIX/7AzCl42dVPLPDW2BBzesQu1OeJbyDkg"
                    + "QD4vPo1aowT4P8AxthcGef42QekixeiUeGZA3FXZzJAjwRWGu3uZBcBPK8SUXs1SF4nKH5R8Qr6KQJM+3xmjSw99uMx9YUbA"
                    + "3a7xMEvPvjmLvYN9V6loV44unKrHUwe7OwmeEWgz7/Upx2sV1D3OruiC+WNXfntR4mt218GWtlx684yamJxvjszXclKONvQB"
                    + "L6yBFkKxi9lvx5v/IqBH/MVDQ0vZM31tId/kwJSgLAVOkbzbGMenyvvKt3vYrGurtRU3QIxq4SB4QejVOdvkaOQsweS7PEDU"
                    + "RafNweVucbvhnUjmR5NmnnIo8p3JO4WjgHhZcFdqVIa7rmEO1Bm/bs6hLWWoIf1cQw9vsyzPZbLUUwDRKwHw1jVxLMLEC8l2"
                    + "vk6UU2HI2Tdm0OkCXjstaI4VFsqla/VllWRmm1yKxyOpGpjwnlVJFfGfOQuo0XDMhIFWUvoV1C5UYiSf22sNFlIbxSWRZ6ga"
                    + "8Iksfch2OeLvQDLQy4rmJ03xuRQKej7XX18Vht1P4y78yGm5hUYyu7c8BSpOq87VOrGP2bdDyaz3t6zolNyeA6gXctQeqbdk"
                    + "zqfXnF5yMHs=");

    /** 各厅的「记录员 / 观察员 / 审察官」 —— 账号 MHF_Villager，Mojang 固定皮肤账号；模型 classic。 */
    static final Skin SCRIBE = new Skin(
            "ewogICJ0aW1lc3RhbXAiIDogMTc5MTk2MjE5Njc4NiwKICAicHJvZmlsZUlkIiA6ICJiZDQ4MjczOTc2N2M0NWRjYTFmOGMz"
                    + "M2M0MDUzMDk1MiIsCiAgInByb2ZpbGVOYW1lIiA6ICJNSEZfVmlsbGFnZXIiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0"
                    + "cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5l"
                    + "Y3JhZnQubmV0L3RleHR1cmUvYjRiZDgzMjgxM2FjMzhlNjg2NDg5MzhkN2EzMmY2YmEyOTgwMWFhZjMxNzQwNDM2N2YyMTRi"
                    + "NzhiNGQ0NzU0YyIKICAgIH0KICB9Cn0=",
            "E94q1OOHomxb40xHDxXF59+3agIFlxmljufPfQorKjHC/d0S9fH55RPiWP1rE802yQJXrYgm4Q5gX+s1H2pxmNCRgO0Qfx4m"
                    + "ZT+NJzRX7JnzfcGZuDwvdtjxUak09ywLwOQPbVDfHshdAX//pAH8Ac8saBTALxDsc/0mOuX9QnwctsHRLZ5xuw+46dkxSXfX"
                    + "MsQxqbiR3x/m128pDVSU/aBJFJa9XOiYa0w+5H20j1Hytl7x10gvjojtbj9Si6iqPu3BOusBJniwl4ThjOClhPv9Zz32Oy2M"
                    + "9zP6P8CEcXS+onAyqtmZQ6k5QITJRd/X9gtWrPzF+6oKjan+qB4ZucKRtk3kOU1i9ykBwNxvrt7sGHTdHL2a2D2CuUPiewW/"
                    + "krmZqx8aJ0KkEaHx4ONQK6yKFFcRNyVlKQ4LWMy5aM4Ebv3kkJufi/DDo2Qu+28hxX1ibrfzRhJuPyK3vUlJz0YSB2ZhefXZ"
                    + "+5IdBfA6gc2vTiV5KODuRPidWvSSVoHXdBb8TTr/BIS0c8S02CMwIZTuTUJ/eYnTS0fOxS/XMruvsvZ6+Rl4Yi6cCXrb+6mk"
                    + "YQ9BBNeLza7VyKU0TeZhaSyFOPAok74n6G/WG+q5c55jyuhBfJgMZaFDgxaUwAjKH3DnHmPebun8TF+fN5YRcWYSlfG7YqiA"
                    + "0EBB8nBSqTY=");

    /** 各厅的猎物替身、可被反复击杀的靶子 —— 账号 MHF_Steve，Mojang 固定皮肤账号；模型 slim。 */
    static final Skin HUMAN = new Skin(
            "ewogICJ0aW1lc3RhbXAiIDogMTc5MTk2MjE5OTQyOSwKICAicHJvZmlsZUlkIiA6ICJjMDZmODkwNjRjOGE0OTExOWMyOWVh"
                    + "MWRiZDFhYWI4MiIsCiAgInByb2ZpbGVOYW1lIiA6ICJNSEZfU3RldmUiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVl"
                    + "LAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3Jh"
                    + "ZnQubmV0L3RleHR1cmUvZDVjNGVlNWNlMjBhZWQ5ZTMzZTg2NmM2NmNhYTM3MTc4NjA2MjM0YjM3MjEwODRiZjAxZDEzMzIw"
                    + "ZmIyZWIzZiIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAg"
                    + "fQp9",
            "eeWQfnZa64xHnDQyKgRa4HesPMKun23bCFcN0LJSUSQaFWV3r14RHEDMP3OJCqj/Etp7vO8goURJjKkwbgIH/qcDPh01Wbg8"
                    + "RdyV4Kq9FMdDx9IgJI2ZrwgYKAChfEFSy3+ClK5kEAG/A97OPBxzHFhL3KlPCiatJjhrN/TZoepsKCuhdMyFL1GqlQrsYw7T"
                    + "Y6X8iE9/CODEVSbTEHRNh7kGa4sjoI6v1oz5c7bC6HC0sXrMHlAaVr1JK9eafw3Us8seZWugcU5jmMe0JKRfJGG6QiuJY0qt"
                    + "i7pAqOB8UTBQOCpG2sloh5QNP9x46e/2TJ2+YAX8Ag66H51Rx0kZpzz+gfRiJZnezAFdhxRJRcoXJTXj1Fs2ldMnPJs0eTuq"
                    + "q8RsHcwZiHpLVO9gNsdGPHqusEI5TdECd9NCKwDmv/WtTf2FELo1F/4EDzf1p5tKYhfHxtwSHkilnB9X6Ff+8iSmr4lZH3c9"
                    + "4/H+pcnQsrHejlLbGjobSfCSjRTONF9cI6N6+yh7mjX7hsEj9m5sIUUmL9QZr8mgh74XyQ9gfdtvn+ZoYFN85+iyqL4SI39u"
                    + "Oe76a3SBOmTIq2UAqGTdUtdB/w+NxcW6ldKy4APoDVegloeN8qq7pQj4CoS8JPaZP5ybYVtgSEY+NRtJonzcyKhWW0I5LBQq"
                    + "RAO+VoPPoN0=");

    /** The skin a race's own guides wear; anything unlisted is an ordinary person. */
    static Skin forRace(String race) {
        return switch (race) {
            case "vampire" -> VAMPIRE;
            case "ghoul" -> GHOUL;
            case "werewolf" -> WEREWOLF;
            case "demon" -> DEMON;
            case "enderborn" -> ENDERBORN;
            case "priest" -> PRIEST;
            case "witchhunter" -> WITCHHUNTER;
            case "angel" -> ANGEL;
            default -> HUMAN;
        };
    }

    private Skins() {
    }
}
