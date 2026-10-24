package com.climaeventos;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Eventos aleatorios de clima y mundo. Casi todo se hace con comandos vanilla.
 * Prueba manual:  /evento   (aleatorio)   o   /evento meteoritos
 */
public class ClimaEventos implements ModInitializer {

    // ===== AJUSTES (en ticks: 20 ticks = 1 segundo) =====
    private static final int MIN_TICKS = 20 * 60 * 3;  // 3 minutos
    private static final int MAX_TICKS = 20 * 60 * 6;  // 6 minutos

    private static final Random RNG = new Random();
    private static int countdown = MIN_TICKS;

    private static final String[] LLUVIA_ITEMS = {
            "apple", "bread", "cooked_beef", "iron_ingot", "gold_ingot", "diamond",
            "emerald", "golden_apple", "arrow", "coal", "redstone", "lapis_lazuli",
            "cookie", "experience_bottle", "ender_pearl", "cake"
    };

    private interface Action { void run(MinecraftServer s, List<String> names); }

    private record Ev(String id, String title, String subtitle, String color, Action action) {}

    private static final List<Ev> EVENTS = List.of(
            new Ev("meteoritos", "Lluvia de meteoritos", "Cuidado con el cielo!", "gold",
                    (s, names) -> {
                        for (String n : names)
                            for (int i = 0; i < 10; i++)
                                at(s, n, "summon falling_block ~" + off(18) + " ~45 ~" + off(18)
                                        + " {BlockState:{Name:\"minecraft:gilded_blackstone\"},Time:1}");
                    }),
            new Ev("items", "Tormenta de items", "Llueven regalos del cielo", "aqua",
                    (s, names) -> {
                        for (String n : names)
                            for (int i = 0; i < 25; i++) {
                                String item = LLUVIA_ITEMS[RNG.nextInt(LLUVIA_ITEMS.length)];
                                at(s, n, "summon item ~" + off(10) + " ~25 ~" + off(10)
                                        + " {Item:{id:\"minecraft:" + item + "\",count:1}}");
                            }
                    }),
            new Ev("sangre", "Noche de sangre", "Los monstruos te rodean...", "dark_red",
                    (s, names) -> {
                        cmd(s, "time set 18000");
                        cmd(s, "weather clear");
                        for (String n : names)
                            for (int i = 0; i < 5; i++)
                                at(s, n, "summon zombie ~" + far(8) + " ~ ~" + far(8));
                    }),
            new Ev("luna", "Gravedad lunar", "Saltas muy alto y caes despacio", "light_purple",
                    (s, names) -> {
                        for (String n : names) {
                            cmd(s, "effect give " + n + " minecraft:jump_boost 90 3 true");
                            cmd(s, "effect give " + n + " minecraft:slow_falling 90 0 true");
                        }
                    }),
            new Ev("tormenta", "Tormenta electrica", "Truenos por todas partes", "yellow",
                    (s, names) -> {
                        cmd(s, "weather thunder 3000");
                        for (String n : names)
                            for (int i = 0; i < 4; i++)
                                at(s, n, "summon lightning_bolt ~" + far(20) + " ~ ~" + far(20));
                    }),
            new Ev("sol", "Bendicion del sol", "Te sientes lleno de energia", "yellow",
                    (s, names) -> {
                        cmd(s, "weather clear");
                        cmd(s, "time set 1000");
                        for (String n : names) {
                            cmd(s, "effect give " + n + " minecraft:regeneration 60 1 true");
                            cmd(s, "effect give " + n + " minecraft:speed 60 1 true");
                            cmd(s, "effect give " + n + " minecraft:haste 60 1 true");
                        }
                    })
    );

    @Override
    public void onInitialize() {
        // Temporizador de eventos automaticos
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getPlayerList().getPlayers().isEmpty()) return;
            if (--countdown <= 0) {
                trigger(server, EVENTS.get(RNG.nextInt(EVENTS.size())));
                countdown = MIN_TICKS + RNG.nextInt(MAX_TICKS - MIN_TICKS + 1);
            }
        });

        // Comando de prueba: /evento  y  /evento <nombre>
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            var root = Commands.literal("evento").executes(ctx -> {
                trigger(ctx.getSource().getServer(), EVENTS.get(RNG.nextInt(EVENTS.size())));
                return 1;
            });
            for (Ev ev : EVENTS) {
                root.then(Commands.literal(ev.id()).executes(ctx -> {
                    trigger(ctx.getSource().getServer(), ev);
                    return 1;
                }));
            }
            dispatcher.register(root);
        });
    }

    private static void trigger(MinecraftServer server, Ev ev) {
        List<String> names = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            names.add(p.getName().getString());
        }
        if (names.isEmpty()) return;

        cmd(server, "tellraw @a " + text("Evento: " + ev.title() + " - " + ev.subtitle(), ev.color()));
        for (String n : names) {
            cmd(server, "title " + n + " times 10 70 20");
            cmd(server, "title " + n + " subtitle " + text(ev.subtitle(), "white"));
            cmd(server, "title " + n + " title " + text(ev.title(), ev.color()));
        }
        ev.action().run(server, names);
    }

    // ---------- utilidades ----------
    private static String text(String t, String color) {
        return "{\"text\":\"" + t + "\",\"color\":\"" + color + "\"}";
    }

    /** numero entre -r y r */
    private static int off(int r) { return RNG.nextInt(r * 2 + 1) - r; }

    /** numero entre -r y r pero nunca cerca de 0 (a 6+ bloques del jugador) */
    private static int far(int r) {
        int v = 6 + RNG.nextInt(Math.max(1, r - 5));
        return RNG.nextBoolean() ? v : -v;
    }

    private static void at(MinecraftServer s, String player, String sub) {
        cmd(s, "execute at " + player + " run " + sub);
    }

    private static void cmd(MinecraftServer s, String command) {
        try {
            s.getCommands().performPrefixedCommand(
                    s.createCommandSourceStack().withSuppressedOutput(), command);
        } catch (Exception e) {
            System.err.println("[ClimaEventos] fallo el comando: " + command + " -> " + e);
        }
    }
}
