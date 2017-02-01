package de.alaoli.games.minecraft.mods.yadm.event.handler;

import java.io.IOException;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.FMLEmbeddedChannel;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import cpw.mods.fml.common.network.FMLOutboundHandler;
import cpw.mods.fml.common.network.handshake.NetworkDispatcher;
import cpw.mods.fml.relauncher.Side;
import de.alaoli.games.minecraft.mods.yadm.Log;
import de.alaoli.games.minecraft.mods.yadm.data.DataException;
import de.alaoli.games.minecraft.mods.yadm.data.Dimension;
import de.alaoli.games.minecraft.mods.yadm.data.DimensionDummy;
import de.alaoli.games.minecraft.mods.yadm.data.settings.SettingType;
import de.alaoli.games.minecraft.mods.yadm.data.settings.WhitelistSetting;
import de.alaoli.games.minecraft.mods.yadm.event.TeleportEvent;
import de.alaoli.games.minecraft.mods.yadm.manager.PlayerManager;
import de.alaoli.games.minecraft.mods.yadm.manager.YADimensionManager;
import de.alaoli.games.minecraft.mods.yadm.manager.dimension.DimensionException;
import de.alaoli.games.minecraft.mods.yadm.network.MessageDispatcher;
import de.alaoli.games.minecraft.mods.yadm.network.SyncDimensionsMessage;
import de.alaoli.games.minecraft.mods.yadm.world.ManageWorlds;
import de.alaoli.games.minecraft.mods.yadm.world.WorldBuilder;
import de.alaoli.games.minecraft.mods.yadm.world.WorldServerGeneric;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.server.management.ServerConfigurationManager;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.world.WorldEvent;

public class DimensionEventHandler 
{
	/********************************************************************************
	 * Attributes
	 ********************************************************************************/
	
	public static final DimensionEventHandler INSTANCE = new DimensionEventHandler();
	
	protected static final PlayerManager players = PlayerManager.INSTANCE;
	protected static final YADimensionManager dimensions = YADimensionManager.INSTANCE;
	protected static final ManageWorlds worlds = WorldBuilder.INSTANCE;
	
	/********************************************************************************
	 * Methods
	 ********************************************************************************/
	
	private DimensionEventHandler() {}
	
	public static void register()
	{
		FMLCommonHandler.instance().bus().register( INSTANCE );
		MinecraftForge.EVENT_BUS.register( INSTANCE );
	}
	
	/********************************************************************************
	 * Methods - FML Events
	 ********************************************************************************/
	
    @SubscribeEvent
    public void onClientConnected( FMLNetworkEvent.ServerConnectionFromClientEvent event )
    {
    	EntityPlayerMP player = ((NetHandlerPlayServer) event.handler).playerEntity;
    	
    	//Register unknown players
    	if( !players.existsPlayer( player ) )
    	{
    		players.addPlayer( player );
    	}
    	
    	//Sync dimensions
    	if( !dimensions.isEmpty() )
    	{
	    	StringBuilder msg = new StringBuilder()
    			.append( "Sync dimensions to Player '" )
	    		.append( player.getUniqueID() )
	    		.append( "':" );
	    	Log.info( msg.toString() );

	    	FMLEmbeddedChannel channel = MessageDispatcher.channels.get( Side.SERVER );
	    	
	    	channel.attr(FMLOutboundHandler.FML_MESSAGETARGET ).set( FMLOutboundHandler.OutboundTarget.DISPATCHER );
	    	channel.attr( FMLOutboundHandler.FML_MESSAGETARGETARGS ).set(event.manager.channel().attr( NetworkDispatcher.FML_DISPATCHER ).get() );
	    	channel.writeOutbound( new SyncDimensionsMessage() );	    	
    	}
    }
    
	/********************************************************************************
	 * Methods - Forge Events
	 ********************************************************************************/
	
    @SubscribeEvent
    public void onEntityJoinWorld( EntityJoinWorldEvent event )
    {
    	//If is YADM dimension check whitelist
    	if( ( !event.world.isRemote ) &&
			( event.entity instanceof EntityPlayerMP ) &&
			( dimensions.existsDimension( event.world.provider.dimensionId ) ) )
    	{
    		try
    		{
    			Dimension dimension = dimensions.findDimension( event.world.provider.dimensionId );
    			
    			if( dimension.hasSetting( SettingType.WHITELIST ) )
    			{
    				EntityPlayerMP player = (EntityPlayerMP)event.entity;
    				WhitelistSetting setting = (WhitelistSetting)dimension.get( SettingType.WHITELIST );
    				ServerConfigurationManager scm = FMLCommonHandler.instance().getMinecraftServerInstance().getConfigurationManager();
    				
    				//Teleport away if player isn't whitelisted excluding operator and owner
    				if( ( !dimension.isOwner( player ) ) &&
						( !scm.canSendCommands( player.getGameProfile() )) &&
						( !setting.exists( player.getUniqueID() ) ) )
					{
    					player.addChatComponentMessage( new ChatComponentText( "You're not whitelisted for dimension '" + dimension + "'."));
    					MinecraftForge.EVENT_BUS.post( new TeleportEvent( new DimensionDummy( 0 ), player ) );
					}
    			}
    		}
    		catch( DimensionException e )
    		{
    			Log.error( e.getMessage() );
    		}
    	}
    }
    
	@SubscribeEvent
	public void onWorldSave( WorldEvent.Save event )
	{
		try 
		{
			players.save();
			dimensions.save();
		}
		catch ( DataException | IOException e )
		{
			e.printStackTrace();
		}
	}

	@SubscribeEvent
	public void onLoadWorld( WorldEvent.Load event )
	{
		//Ugly workaround to prevent loading "vanilla" WorldServer
		if( ( !event.world.isRemote ) &&
			( dimensions.existsDimension( event.world.provider.dimensionId ) ) &&
			( !(event.world instanceof WorldServerGeneric) ) )
		{
			Dimension dimension = dimensions.findDimension( event.world.provider.dimensionId );
			worlds.getWorldServerForDimension( dimension );
		}
			
	}
	
	@SubscribeEvent
	public void onUnloadWorld( WorldEvent.Unload event )
	{
		//Cleanup deleted dimensions
		if( !event.world.isRemote )
		{
			worlds.deleteWorld(  event.world );
		}
	}
}
