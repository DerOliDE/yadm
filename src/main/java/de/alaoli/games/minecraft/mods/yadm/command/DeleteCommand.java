package de.alaoli.games.minecraft.mods.yadm.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import de.alaoli.games.minecraft.mods.yadm.YADM;
import de.alaoli.games.minecraft.mods.yadm.data.Dimension;
import de.alaoli.games.minecraft.mods.yadm.manager.YADimensionManager;
import de.alaoli.games.minecraft.mods.yadm.util.CommandUtil;
import de.alaoli.games.minecraft.mods.yadm.util.TeleportUtil;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.WorldServer;

public class DeleteCommand extends Command
{
	/********************************************************************************
	 * Methods
	 ********************************************************************************/
	
	public DeleteCommand( Command parent ) 
	{
		super( parent );
	}

	/********************************************************************************
	 * Override - ICommand, Command
	 ********************************************************************************/
	
	@Override
	public int getRequiredPermissionLevel() 
	{
		return 2;
	}	
	
	@Override
	public String getCommandName() 
	{
		return "delete";
	}

	@Override
	public String getCommandUsage( ICommandSender sender ) 
	{
		return super.getCommandUsage( sender ) + " <dimensionName>";
	}
	
	@Override
	public void processCommand( ICommandSender sender, Queue<String> args ) 
	{
		if( args.isEmpty() )
		{
			sender.addChatMessage( new ChatComponentText( this.getCommandUsage( sender ) ) );
			return;
		}
		Dimension dimension = CommandUtil.parseDimension( sender, args );
		
		if( dimension == null ) { return; }
		
		WorldServer world = MinecraftServer.getServer().worldServerForDimension( dimension.getId() );
		List<EntityPlayerMP> players = new ArrayList<EntityPlayerMP>(world.playerEntities);
		
		//Teleport all players out
		for( EntityPlayerMP player : players )
		{
			TeleportUtil.emergencyTeleport( player );
		}
		YADimensionManager.instance.delete( dimension );
		YADM.proxy.unregisterDimension( dimension );
		sender.addChatMessage( new ChatComponentText( "Dimension '" + dimension.getName() + "' removed." ) );
	}
}