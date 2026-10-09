package com.regayagamtor.regayasecurity.listener;

import com.regayagamtor.regayasecurity.RegayaSecurity;
import com.regayagamtor.regayasecurity.manager.SearchSessionManager.HitResult;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Pukulan pemicu penggeledahan paksa. Pembatalan HANYA berlaku untuk satu pukulan pemicu dari
 * penggeledah ke target yang benar; PvP global tidak disentuh.
 *
 * PrePlayerAttackEntityEvent dibatalkan sebelum serangan diproses: tidak ada damage, knockback,
 * efek sapuan, pengurangan durability senjata/armor. EntityDamageByEntityEvent hanya cadangan
 * bila ada jalur serangan lain pada tick yang sama.
 *
 * ignoreCancelled sengaja false: pukulan tetap dikenali sebagai pemicu walau plugin lain
 * (mis. zona anti-PvP) membatalkannya, karena ini bukan serangan sungguhan.
 */
public final class CombatListener implements Listener {

    private final RegayaSecurity plugin;

    public CombatListener(RegayaSecurity plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreAttack(PrePlayerAttackEntityEvent event) {
        HitResult result = plugin.sessions().handleTriggerHit(event.getPlayer(), event.getAttacked());
        if (result == HitResult.TRIGGERED || result == HitResult.TRIGGERED_DUPLICATE) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        HitResult result = plugin.sessions().handleTriggerHit(attacker, event.getEntity());
        if (result == HitResult.TRIGGERED || result == HitResult.TRIGGERED_DUPLICATE) {
            event.setCancelled(true);
        }
    }
}
