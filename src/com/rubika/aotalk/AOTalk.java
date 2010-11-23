package com.rubika.aotalk;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.app.AlertDialog.Builder;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnCancelListener;
import android.os.Bundle;
import android.os.IBinder;
import android.text.Editable;
import android.text.Selection;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.view.View.OnLongClickListener;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;

import ao.misc.AONameFormat;
import ao.protocol.AOBot;
import ao.protocol.AODimensionAddress;
import ao.protocol.packets.bi.AOPrivateGroupInvitePacket;
import ao.protocol.packets.in.AOCharListPacket;

public class AOTalk extends Activity {
	protected static final String APPTAG = "--> AOTalk";

	private BroadcastReceiver messageReceiver    = new AOBotMessageReceiver();
	private BroadcastReceiver connectionReceiver = new AOBotConnectionReceiver();

	private ServiceConnection conn;
	private AOBotService bot;
	private ChatParser chat;
	
	private String PREFSNAME = "AOTALK";
	private String PASSWORD  = "";
	private String USERNAME  = "";
	private boolean SAVEPREF = false;
	private boolean FULLSCRN = false;
	
	private final String CHANNEL_MSG = "Private Message";
	private final String CHANNEL_FRIEND = "Friend";
	private String CHATCHANNEL = "";
	private String MESSAGETO   = "";
	private String LASTMESSAGE = "";
	
	private Button channelbutton;
	private EditText msginput;
	private Context context;
	private ProgressDialog loader;
	
	private List<String> predefinedText;
	private List<String> groupDisable;
	private List<ChatMessage> messages;

	private ListView messagelist;
	private ChatMessageAdapter msgadapter;
	
	private boolean welcome = true;
			
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        //Load values that are saved from last time the app was used
        SharedPreferences settings = getSharedPreferences(PREFSNAME, 0);
        SAVEPREF = settings.getBoolean("savepref", SAVEPREF);
        USERNAME = settings.getString("username", USERNAME);
        PASSWORD = settings.getString("password", PASSWORD);
        CHATCHANNEL = settings.getString("chatchannel", CHATCHANNEL);
        MESSAGETO = settings.getString("messageto", MESSAGETO);
        FULLSCRN = settings.getBoolean("fullscreen", FULLSCRN);
        
        if(AOTalk.this.FULLSCRN) {
        	getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);	
        }
        
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.main);
        
        if(AOTalk.this.FULLSCRN) {
        	LinearLayout titlebar = (LinearLayout) findViewById(R.id.headwrap);
        	titlebar.setVisibility(View.GONE);
    	}
        
        context = this;
        
        //Predefined text string, user can chose between them when long pressing input field
        predefinedText = new ArrayList<String>();
        predefinedText.add("!afk");
        predefinedText.add("!online");
        predefinedText.add("!join");
        predefinedText.add("!items ");

        chat = new ChatParser();
        
        groupDisable = new ArrayList<String>();
        messages = new ArrayList<ChatMessage>();
        
        messagelist = (ListView)findViewById(R.id.messagelist);
        messagelist.setTranscriptMode(ListView.TRANSCRIPT_MODE_ALWAYS_SCROLL);
        
        //Disable automatic pop up of keyboard at launch
        this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        
        msgadapter = new ChatMessageAdapter(this, messages);
        messagelist.setAdapter(msgadapter);
        messagelist.setFocusable(true);
        messagelist.setFocusableInTouchMode(true);
        messagelist.setItemsCanFocus(true);
        
        messagelist.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener(){
			@Override
			public boolean onItemLongClick(AdapterView<?> av, View v, int pos, long id) {
		    	ChatMessage message = messages.get(pos);
		    	String tmpToCharacter  = "";
		    	String tmpToChannel    = "";
		    	String tmpCharacter    = "";
		    	String tmpChannel      = "";
		    	String tmpLeaveChannel = "";
		    	
		     	int countArrayAlts = 0;
		    	
		    	if(message.getChannel() != null) {
			     	if(message.getChannel().startsWith(AOBotService.PRIVATE_GROUP_PREFIX)) {
			     		countArrayAlts++;
			     	}
		    		countArrayAlts++;
		    	}
		     	
		     	if(message.getCharacter() != null) {
		     		countArrayAlts++;
		    	}
		     	
		     	CharSequence tmpOptions[] = new CharSequence[countArrayAlts];
		     	
		     	int countMenuAlts = 0;

		    	if(message.getChannel() != null) {
			     	if(message.getChannel().startsWith(AOBotService.PRIVATE_GROUP_PREFIX)) {
			     		tmpLeaveChannel = "Leave " + message.getChannel();
			     		tmpOptions[countMenuAlts] = tmpLeaveChannel;
			    		tmpChannel = message.getChannel();
			     		countMenuAlts++;
			     	}
			     	
			     	tmpToChannel = AOTalk.this.getString(R.string.group_message_to) + " " + message.getChannel();
		    		tmpOptions[countMenuAlts] = tmpToChannel;
		    		tmpChannel = message.getChannel();
		    		countMenuAlts++;
		    	}
		     	
		     	if(message.getCharacter() != null) {
		    		tmpToCharacter = AOTalk.this.getString(R.string.private_message_to) + " " + message.getCharacter();
		    		tmpOptions[countMenuAlts] = tmpToCharacter;
		    		tmpCharacter = message.getCharacter();
		    		countMenuAlts++;
		    	}
		    	
		    	if(countMenuAlts > 0) {
			    	final CharSequence options[] = tmpOptions;
			    	final String toCharacter = tmpToCharacter;
			    	final String toChannel = tmpToChannel;
			    	final String character = tmpCharacter;
			    	final String channel = tmpChannel;
			    	final String leaveChannel = tmpLeaveChannel;
			   	
			    	AlertDialog.Builder builder = new AlertDialog.Builder(AOTalk.this);
			    	builder.setTitle("Message options");
			    	builder.setItems(options, new DialogInterface.OnClickListener() {
			    	    public void onClick(DialogInterface dialog, int item) {
			    	    	if(options[item].toString().equals(leaveChannel)) {
			    	    		AOTalk.this.bot.leaveGroup(channel.replace(AOBotService.PRIVATE_GROUP_PREFIX, ""));
			    	    		AOTalk.this.CHATCHANNEL = "";
			    	    		AOTalk.this.MESSAGETO = "";
			    	    		Log.d(APPTAG, "Leaving group...");
			    	    		setButtonText();
			    	    	} else if(options[item].toString().equals(toCharacter)) {
			    	    		AOTalk.this.CHATCHANNEL = AOTalk.this.CHANNEL_MSG;
			    	    		AOTalk.this.MESSAGETO = character;
			    	    		setButtonText();
			    	    	} else if(options[item].toString().equals(toChannel)) {
			    	    		AOTalk.this.CHATCHANNEL = channel;
			    	    		setButtonText();
			    	    	}
			    	    }
			    	});
			    	
			    	AlertDialog optionlist = builder.create();
			    	optionlist.show();
			    	
			    	return true;
		    	} else {
		    		return false;
		    	}
			}
        });
        
        messages.add(new ChatMessage(
        		new Date().getTime(),
        		chat.parse("<br /><b>" + getString(R.string.welcome) + "</b>" + getString(R.string.about),
        		ChatParser.TYPE_PLAIN_MESSAGE), 
        		null, 
        		null
        ));
               
        channelbutton = (Button) findViewById(R.id.msgchannel);
        channelbutton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				setChannel();
			}
		});
        
        setButtonText();
		
        msginput = (EditText) findViewById(R.id.msginput);
        msginput.setOnKeyListener(new OnKeyListener() {
			@Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
				if ((event.getAction() == KeyEvent.ACTION_DOWN) && (keyCode == KeyEvent.KEYCODE_ENTER)) {
					if(AOTalk.this.bot.getState() == ao.protocol.AOBot.State.LOGGED_IN && msginput.getText().toString().length() > 0) {				
                		//Send message
                		if(CHATCHANNEL.equals(CHANNEL_MSG)) {
                			AOTalk.this.bot.sendTell(AOTalk.this.MESSAGETO, msginput.getText().toString(), true);
                			getMessages();
                			
							Log.d(APPTAG, "Sent private message to " + MESSAGETO + ": " + msginput.getText().toString());
                		} else { //Send to group
                			if(CHATCHANNEL.startsWith(AOBotService.PRIVATE_GROUP_PREFIX)) {
                				AOTalk.this.bot.sendPGMsg(AOTalk.this.CHATCHANNEL.replace(AOBotService.PRIVATE_GROUP_PREFIX, ""), msginput.getText().toString());
                				Log.d(APPTAG, "Sent private group message to " + 
                						CHATCHANNEL.replace(AOBotService.PRIVATE_GROUP_PREFIX, "") + 
                						": " + msginput.getText().toString()
                				);
                				
                			} else {
                				AOTalk.this.bot.sendGMsg(AOTalk.this.CHATCHANNEL, msginput.getText().toString());
                				Log.d(APPTAG, "Sent group message to " + CHATCHANNEL + ": " + msginput.getText().toString());
                			}
                		}
                		
                		AOTalk.this.LASTMESSAGE = AOTalk.this.msginput.getText().toString();
                		AOTalk.this.msginput.setText("");

	                	return true;
					} else {
						Log.d(APPTAG, "Not logged in or no message, can't send message");
                	}
                }
				
                return false;
            }
        });
        
        /* Future calls when clicking on input field
        msginput.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				...
			}
		});
		*/
        
        //Long click on input lets user select from last message and predefined texts
        msginput.setOnLongClickListener(new OnLongClickListener() {
			@Override
			public boolean onLongClick(View v) {
				showPredefinedText();
				return false;
			}
		});
        
        //Connect to the bot service
        attachToService();
    }
    
    
    //Change the text on the channel button
    private void setButtonText() {
		if(AOTalk.this.CHATCHANNEL != "") {
			if(AOTalk.this.CHATCHANNEL.equals(AOTalk.this.CHANNEL_MSG)) {
				if(!AOTalk.this.MESSAGETO.equals("")) {
					AOTalk.this.channelbutton.setText(
						AOTalk.this.getString(R.string.tell) + ": " + AONameFormat.format(AOTalk.this.MESSAGETO)
					);
				} else {
					AOTalk.this.channelbutton.setText(AOTalk.this.getString(R.string.select_channel));
				}
	    	} else {
	    		AOTalk.this.channelbutton.setText(CHATCHANNEL);
	    	}
		} else {
			AOTalk.this.channelbutton.setText(AOTalk.this.getString(R.string.select_channel));
		}   	
    }

    
    //Displays a list of predefined text (and the last message user made) when long pressing the input field
    private void showPredefinedText() {
    	CharSequence tempTexts[] = null;
    	int adder = 0;
    	
    	if(predefinedText != null) {
 	    	if((!predefinedText.contains(AOTalk.this.LASTMESSAGE) && (!AOTalk.this.LASTMESSAGE.equals("")))) {
	    		adder = 1;
	    	} 
 	    	
 	    	tempTexts = new CharSequence[predefinedText.size() + adder];
 	    	
	    	for(int i = 0; i < predefinedText.size() + adder; i++) {
	    		if(i == 0 && adder > 0) {
	    			tempTexts[i] = AOTalk.this.LASTMESSAGE;
	    		} else {
	    			tempTexts[i] = predefinedText.get(i - adder);
	    		}
	    	}
    	}
     	
    	final CharSequence[] texts = tempTexts;

    	AlertDialog.Builder builder = new AlertDialog.Builder(this);
    	builder.setTitle(AOTalk.this.getString(R.string.select_message));
    	builder.setItems(texts, new DialogInterface.OnClickListener() {
    	    public void onClick(DialogInterface dialog, int item) {
    	    	AOTalk.this.msginput.setText(texts[item]);
    	    	
    	    	//Move cursor to the end of the text
    	    	Editable etext = AOTalk.this.msginput.getText();
    	    	int position = etext.length();
    	    	Selection.setSelection(etext, position);
    	    }
    	});
    	
    	AlertDialog textlist = builder.create();
    	textlist.show();
    }

    
    //Show a pop up when a new invitation is received
    private void handleInvitation() {
    	final AOPrivateGroupInvitePacket invitation = AOTalk.this.bot.getInvitation();
    	
    	AlertDialog joinGroupDialog = new AlertDialog.Builder(AOTalk.this).create();
    	joinGroupDialog.setTitle(AOTalk.this.bot.getCharTable().getName(invitation.getGroupdID()));
    	joinGroupDialog.setMessage(AOTalk.this.getString(R.string.join_group));
    		            
    	joinGroupDialog.setButton(AOTalk.this.getString(R.string.ok), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
		    	AOTalk.this.bot.acceptInvitation(invitation.getGroupdID());
				return;
			} 
		});
		
    	joinGroupDialog.setButton2(AOTalk.this.getString(R.string.cancel), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				AOTalk.this.bot.rejectInvitation(invitation.getGroupdID());
				return;
			}
		}); 
    	
    	joinGroupDialog.show();   	
    }
    
    //Load last messages from the bot service
    private void getMessages() {
    	if(AOTalk.this.bot.getMessagesSize() > 0) {
    		if(AOTalk.this.welcome) {
    			AOTalk.this.messages.clear();
    			AOTalk.this.welcome = false;
    		}
    		
    		List<ChatMessage> temp = AOTalk.this.bot.getLastMessages(AOTalk.this.messages.size());
    		
    		if(temp != null) {
	    		for(int i = 0; i < temp.size(); i++) {
	    			AOTalk.this.messages.add(temp.get(i));
	    			AOTalk.this.msgadapter.notifyDataSetChanged();
	    		}
    		}
    	}
    }
    
    
    //Let user select server during the connection
	private void setServer() {
    	final CharSequence servers[] = {"Atlantean", "Rimor", "TestLive"};

    	AlertDialog.Builder builder = new AlertDialog.Builder(this);
    	builder.setTitle(AOTalk.this.getString(R.string.select_server));
    	builder.setItems(servers, new DialogInterface.OnClickListener() {
    	    public void onClick(DialogInterface dialog, int item) {
    	    	loader = new ProgressDialog(context);
		    	loader.setProgressStyle(ProgressDialog.STYLE_SPINNER);
		    	loader.setTitle(getResources().getString(R.string.connecting));
				loader.setMessage(getResources().getString(R.string.please_wait));
				loader.show();
    	    	
    	    	if(servers[item].toString().equals("Atlantean")) {
    	    		new Thread() {
    		            public void run() {
    		            	AOTalk.this.bot.setServer(AODimensionAddress.RK1);
    		        	}
    				}.start();
    	    	} else if(servers[item].toString().equals("Rimor")) {
    	    		new Thread() {
    		            public void run() {
    		            	AOTalk.this.bot.setServer(AODimensionAddress.RK2);
    		        	}
    				}.start();
    	    	} else {
    	    		new Thread() {
    		            public void run() {
    		            	AOTalk.this.bot.setServer(AODimensionAddress.TEST);
    		        	}
    				}.start();
    	    	}
    	    }
    	});
    	
    	AlertDialog serverlist = builder.create();
    	serverlist.show();
    }
    
	
	//Lets the user select character during connection
	private void setCharacter() {
    	final AOCharListPacket charpacket = bot.getCharPacket();

    	if(charpacket != null) {
	    	CharSequence names[] = new CharSequence[charpacket.getNumCharacters()];
	    	
    		for(int i = 0; i < charpacket.getNumCharacters(); i++) {
    			names[i] = charpacket.getCharacter(i).getName();
	    	}

	    	final CharSequence[] charlist = names;
	
	    	AlertDialog.Builder builder = new AlertDialog.Builder(this);
	    	builder.setTitle(AOTalk.this.getString(R.string.select_character));
	    	builder.setItems(charlist, new DialogInterface.OnClickListener() {
	    	    public void onClick(DialogInterface dialog, int item) {
	    	    	AOTalk.this.bot.setCharacter(charpacket.findCharacter(AONameFormat.format(charlist[item].toString())));
	    	    }
	    	});
	    	
	    	builder.setOnCancelListener(new OnCancelListener() {
				@Override
				public void onCancel(DialogInterface dialog) {
					AOTalk.this.bot.disconnect();
				}}
	    	);
	    	
	    	AlertDialog characters = builder.create();	    	
	    	characters.show();
    	} else {
    		Log.d(APPTAG, "Character packet is NULL");
    	}
    }
    
	
    private void setChannel() {
    	CharSequence[] tempChannels = null;
    	List<String> groupList = AOTalk.this.bot.getGroupList();
    	
    	if(groupList != null) {
    		tempChannels = new CharSequence[groupList.size() + 1]; //groupList.size() + 2
	    	for(int i = 0; i <= groupList.size(); i++) {
	    		if(i == 0) {
	    			tempChannels[i] = AOTalk.this.CHANNEL_MSG;
	    		}/* else if(i == 1) {
	    			tempChannels[i] = AOTalk.this.CHANNEL_FRIEND;
	    		}*/ else {
	    			tempChannels[i] = groupList.get(i - 1);
	    		}
	    	} 
    	}
     	
    	final CharSequence[] channellist = tempChannels;

    	AlertDialog.Builder builder = new AlertDialog.Builder(this);
    	builder.setTitle(AOTalk.this.getString(R.string.select_channel));
    	
    	builder.setItems(channellist, new DialogInterface.OnClickListener() {
    	    public void onClick(DialogInterface dialog, int item) {
         	    AOTalk.this.CHATCHANNEL = channellist[item].toString();
         	    
         	    if(channellist[item].toString().equals(AOTalk.this.CHANNEL_MSG)) {
     	        	LayoutInflater inflater = (LayoutInflater)AOTalk.this.getSystemService(LAYOUT_INFLATER_SERVICE);
    	            final View layout = inflater.inflate(R.layout.sendto,(ViewGroup) findViewById(R.layout.sendto));
    	            
    	        	Builder builder = new AlertDialog.Builder(context);
    	        	builder.setTitle(getResources().getString(R.string.send_to_title));
    	        	builder.setView(layout);
    	        	
    	    		EditText TargetEditText = (EditText) layout.findViewById(R.id.targetname);
    	    		TargetEditText.setText(AOTalk.this.MESSAGETO);
    	        	
    	        	builder.setPositiveButton(AOTalk.this.getString(R.string.ok), new DialogInterface.OnClickListener() {
    	    			public void onClick(DialogInterface dialog, int which) {
    	    				EditText TargetEditText = (EditText) layout.findViewById(R.id.targetname);
    	    				AOTalk.this.MESSAGETO = TargetEditText.getText().toString();
    	    				
    	    				setButtonText();
    	    				return;
    	    			}
    	    		});
    	        	
    	        	AlertDialog targetbox = builder.create();
    	        	targetbox.show();    	    		
    	    	} else if(channellist[item].toString().equals(AOTalk.this.CHANNEL_FRIEND)) {
    	    		//Friend selected...
    	    	} else {
    	    		setButtonText();
    	    	}
    	    }
    	});
    	
    	AlertDialog channels = builder.create();
    	channels.show();
    }
    
    
    private void setAccount() {
    	LayoutInflater inflater = (LayoutInflater)this.getSystemService(LAYOUT_INFLATER_SERVICE);
        final View layout = inflater.inflate(R.layout.login,(ViewGroup) findViewById(R.layout.login));
    	Builder builder = new AlertDialog.Builder(context);
    	builder.setTitle(getResources().getString(R.string.login_title));
    	builder.setView(layout);
    	
		EditText UserEditText = (EditText) layout.findViewById(R.id.username);
		EditText PassEditText = (EditText) layout.findViewById(R.id.password);
		CheckBox SavePrefs    = (CheckBox) layout.findViewById(R.id.savepassword);
		
		UserEditText.setText(USERNAME);
    	PassEditText.setText(PASSWORD);
    	SavePrefs.setChecked(SAVEPREF);
    	
    	builder.setPositiveButton(AOTalk.this.getString(R.string.ok), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {  	                											
				EditText UserEditText = (EditText) layout.findViewById(R.id.username);
				EditText PassEditText = (EditText) layout.findViewById(R.id.password);
				CheckBox SavePrefs    = (CheckBox) layout.findViewById(R.id.savepassword);
				
				AOTalk.this.USERNAME = UserEditText.getText().toString();
				AOTalk.this.PASSWORD = PassEditText.getText().toString();
				AOTalk.this.SAVEPREF = SavePrefs.isChecked();
				
				loader = new ProgressDialog(context);
		    	loader.setProgressStyle(ProgressDialog.STYLE_SPINNER);
		    	loader.setTitle(getResources().getString(R.string.connecting));
				loader.setMessage(getResources().getString(R.string.please_wait));
				loader.show();
				
				new Thread() {
		            public void run() {
		            	AOTalk.this.bot.setAccount(AOTalk.this.USERNAME, AOTalk.this.PASSWORD);
		        	}
				}.start();
				
				return;
			} 
		});
		
    	builder.setNegativeButton(AOTalk.this.getString(R.string.cancel), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				if(AOTalk.this.bot.getState() != AOBot.State.DISCONNECTED) {
					AOTalk.this.bot.disconnect();
				}
				return;
			}
		}); 
    	
    	builder.setOnCancelListener(new OnCancelListener() {
			@Override
			public void onCancel(DialogInterface dialog) {
				if(AOTalk.this.bot.getState() != AOBot.State.DISCONNECTED) {
					AOTalk.this.bot.disconnect();
				}
				return;
			}
    	});
    	
    	AlertDialog loginbox = builder.create();
    	loginbox.show();
	}
    
    
    private void clearLog() {
    	AlertDialog clearDialog = new AlertDialog.Builder(AOTalk.this).create();
    	
    	clearDialog.setTitle(AOTalk.this.getString(R.string.clear_chat_log));
    	clearDialog.setMessage(getResources().getString(R.string.want_to_clear));
    	clearDialog.setIcon(R.drawable.icon_clear);
    		            
    	clearDialog.setButton(AOTalk.this.getString(R.string.ok), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				AOTalk.this.messages.clear();
				AOTalk.this.bot.clearLog();
				
				AOTalk.this.messagelist.post(new Runnable() {
					@Override
					public void run() {
						AOTalk.this.msgadapter.notifyDataSetChanged();
					}
				});
				
				return;
			} 
		});
		
    	clearDialog.setButton2(AOTalk.this.getString(R.string.cancel), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				return;
			}
		}); 
    	
    	clearDialog.show();
    }
    
    
    private void settings() {   	
    	final List<String> groupDisable = AOTalk.this.bot.getDisabledGroups();
    	final List<String> groupList = AOTalk.this.bot.getGroupList();
    	
    	CharSequence[] tempChannels = null;
    	boolean[] channelStates = null;
    	
    	if(groupList != null) {
    		tempChannels = new CharSequence[groupList.size()];
    		channelStates = new boolean[groupList.size()];
    		
	    	for(int i = 0; i < groupList.size(); i++) {
	    		tempChannels[i] = groupList.get(i);
				if(groupDisable != null ) {
		    		if(groupDisable.contains(groupList.get(i))) {
						channelStates[i] = true;
					} else {
						channelStates[i] = false;
					}
				} else {
					channelStates[i] = false;
				}
	    	} 
    	}
     	
    	final CharSequence[] channellist = tempChannels;

    	AlertDialog.Builder builder = new AlertDialog.Builder(this);
    	builder.setTitle(AOTalk.this.getString(R.string.disable_channels));
    	
    	builder.setMultiChoiceItems(channellist, channelStates, new DialogInterface.OnMultiChoiceClickListener() {
    	    @Override
			public void onClick(DialogInterface dialog, int which, boolean isChecked) {
				if(isChecked) {
					if(!groupDisable.contains(channellist[which].toString())) {
						groupDisable.add(channellist[which].toString());
					}
				} else {
					if(groupDisable.contains(channellist[which].toString())) {
						groupDisable.remove(channellist[which].toString());
					}
				}
				
				AOTalk.this.bot.setDisabledGroups(groupDisable);
			}
    	});
    	
    	builder.setPositiveButton(AOTalk.this.getString(R.string.ok), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {  	                						
				AOTalk.this.bot.setDisabledGroups(groupDisable);
				return;
			} 
		});
    	
    	AlertDialog settingsbox = builder.create();
    	settingsbox.show();	
    }
    
    
    @Override
    public void onResume() {
    	super.onResume();
    	
        //Load values that are saved from last time the app was used
        SharedPreferences settings = getSharedPreferences(PREFSNAME, 0);
        SAVEPREF = settings.getBoolean("savepref", SAVEPREF);
        USERNAME = settings.getString("username", USERNAME);
        PASSWORD = settings.getString("password", PASSWORD);
        CHATCHANNEL = settings.getString("chatchannel", CHATCHANNEL);
        MESSAGETO = settings.getString("messageto", MESSAGETO);
        FULLSCRN = settings.getBoolean("fullscreen", FULLSCRN);
    	
        String temp[] = settings.getString("disabledchannels", "").split(",");
        
        for(int i = 0; i < temp.length; i++) {
        	groupDisable.add(temp[i]);
        }
        
        attachToService();
    }
    
    
    @Override
    public void onRestart() {
    	super.onRestart();
    }
    
    
    @Override
    public void onPause() {
        super.onPause();      
        savePreferences();
	}
    
    
    @Override
    public void onStop() {
    	super.onStop();
   	
    	savePreferences();
    	unregisterReceivers();
    }
    
    
    @Override
    public void onDestroy() {
    	super.onDestroy();
    }
    
    
    private void savePreferences() {
		SharedPreferences settings = getSharedPreferences(PREFSNAME, 0);
		SharedPreferences.Editor editor = settings.edit();
		
		editor.putBoolean("savepref", SAVEPREF);
		editor.putString("chatchannel", CHATCHANNEL);
		editor.putString("messageto", MESSAGETO);
		editor.putBoolean("fullscreen", FULLSCRN);
		
		String disabledChannels = "";
		List<String> dc = AOTalk.this.bot.getGroupDisableList();
		
		for(int i = 0; i < dc.size(); i++) {
			disabledChannels += dc.get(i);
			if(i > 0 && i < dc.size() - 1) {
				disabledChannels += ",";
			}
		}
		
		editor.putString("disabledchannels", disabledChannels);
		
		if(SAVEPREF) {
			editor.putString("username", USERNAME);
			editor.putString("password", PASSWORD);
		} else {
			editor.putString("username", "");
			editor.putString("password", "");			
		}
		
		editor.commit();
    }
	
    
    private void registerReceivers() {
    	this.registerReceiver(messageReceiver, new IntentFilter(AOBotService.INFO_MESSAGE));    	
	    this.registerReceiver(connectionReceiver, new IntentFilter(AOBotService.INFO_CONNECTION));    	
    }
    
    
    private void unregisterReceivers() {
    	this.unregisterReceiver(messageReceiver);
    	this.unregisterReceiver(connectionReceiver);
    }
    
    
	private class AOBotConnectionReceiver extends BroadcastReceiver {
	    @Override
	    public void onReceive(Context context, Intent intent) {
	    	String value = intent.getStringExtra(AOBotService.EXTRA_CONNECTION);
	    	
	    	if(value.equals(AOBotService.CON_SERVER)) {
	    		setServer();
	    	}
	    	
	    	if(value.equals(AOBotService.CON_ACCOUNT)) {
		    	if(AOTalk.this.loader != null) {
		    		AOTalk.this.loader.dismiss();
		    		AOTalk.this.loader = null;
		    	}
	    		
	    		setAccount();
	    	}
	    	
	    	if(value.equals(AOBotService.CON_CHARACTER)) {
	    		setCharacter();
	    	}
	    	
	    	if(value.equals(AOBotService.CON_LFAILURE)) {
		    	AOTalk.this.bot.appendToLog(
		    		chat.parse(AOTalk.this.getString(R.string.could_not_log_in), ChatParser.TYPE_CLIENT_MESSAGE),
		    		null,
		    		null
		    	);
		    }
	    	
	    	if(value.equals(AOBotService.CON_CFAILURE)) {
		    	AOTalk.this.bot.appendToLog(
		    		chat.parse(AOTalk.this.getString(R.string.could_not_connect), ChatParser.TYPE_CLIENT_MESSAGE),
		    		null,
		    		null
		    	);

		    	if(AOTalk.this.loader != null) {
		    		AOTalk.this.loader.dismiss();
		    		AOTalk.this.loader = null;
		    	}
	    	}
	    	
	    	if(value.equals(AOBotService.CON_CONNECTED)) {
	    		AOTalk.this.bot.appendToLog(
	    			chat.parse(AOTalk.this.getString(R.string.connected), ChatParser.TYPE_CLIENT_MESSAGE),
	    			null,
	    			null
	    		);

		    	if(AOTalk.this.loader != null) {
		    		AOTalk.this.loader.dismiss();
		    		AOTalk.this.loader = null;
		    	}
	    	}
	    	
	    	if(value.equals(AOBotService.CON_DISCONNECTED)) {
		    	AOTalk.this.bot.appendToLog(
		    		chat.parse(AOTalk.this.getString(R.string.disconnected), ChatParser.TYPE_CLIENT_MESSAGE),
		    		null,
		    		null
		    	);
		    	
		    	if(AOTalk.this.loader != null) {
	    			AOTalk.this.loader.dismiss();
	    			AOTalk.this.loader = null;
	    		}
	    	}
	    	
	    	if(value.equals(AOBotService.CON_INVITE)) {
		    	handleInvitation();
	    	}
	    	
	    	getMessages();
	    	Log.d(APPTAG, "AOBotConnectionReceiver received message");
	    }
	}
	
	
	private class AOBotMessageReceiver extends BroadcastReceiver {
	    @Override
	    public void onReceive(Context context, Intent intent) {
	    	getMessages();
	    	Log.d(APPTAG, "AOBotMessageReceiver received message");
	    }
	}
	
	
	private void attachToService() {
		Intent serviceIntent = new Intent(this, AOBotService.class);
	    
	    conn = new ServiceConnection() {
			@Override
			public void onServiceConnected(ComponentName name, IBinder service) {
				AOTalk.this.bot = ((AOBotService.ListenBinder) service).getService();
				
				getMessages();
				
				AOTalk.this.messagelist.post(new Runnable() {
					@Override
					public void run() {
						AOTalk.this.msgadapter.notifyDataSetChanged();
					}
				});
			}
			
			@Override
			public void onServiceDisconnected(ComponentName name) {
				AOTalk.this.bot = null;
			}
	    };

	    this.getApplicationContext().startService(serviceIntent);
	    this.getApplicationContext().bindService(serviceIntent, conn, 0);
	    
	    registerReceivers();
	}
	
	
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.layout.mainmenu, menu);
        
        return true;
    }
    
    
    public boolean onOptionsItemSelected(MenuItem item) {
    	switch (item.getItemId()) {
	        case R.id.connect:
	        	if(AOTalk.this.bot.getState() == AOBot.State.DISCONNECTED) {
		        	AOTalk.this.bot.connect();
		        	AOTalk.this.messages.clear();
	        	}
	            return true;
	        case R.id.disconnect:
	        	AOTalk.this.bot.disconnect();
	        	return true;
	        case R.id.clear:
	        	clearLog();
	        	return true;
	        /*
	        case R.id.fullscreen:
	        	LinearLayout titlebar = (LinearLayout) findViewById(R.id.headwrap);
	        	
	        	//Toggle fullscreen
	        	if(titlebar.getVisibility() != View.GONE) {
		        	titlebar.setVisibility(View.GONE);
		        	getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
		            AOTalk.this.FULLSCRN = true;
	        	} else {
		        	titlebar.setVisibility(View.VISIBLE);
		        	getWindow().addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
		            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);	        		
		            AOTalk.this.FULLSCRN = false;
	        	}
	        	
	        	return true;
	        */
	        case R.id.settings:
	        	settings();
	        	return true;
	        default:
	            return super.onOptionsItemSelected(item);
        }
    }
}