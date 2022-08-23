package com.omicron.playtime_commands.mixin;

import com.omicron.playtime_commands.PlaytimeCommands;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.realms.dto.RealmsServerPlayerList;
import net.minecraft.network.ClientConnection;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerManager.class)
public class ExampleMixin {
	@Inject(at = @At("RETURN"), method = "onPlayerConnect")
	private void init(ClientConnection connection, ServerPlayerEntity player, CallbackInfo ci) {
		PlaytimeCommands.onPlayerConnect(connection, player);
	}
}
