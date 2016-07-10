package com.firebase.androidchat.activity;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.DataSetObserver;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import com.firebase.androidchat.ChatApplication;
import com.firebase.androidchat.R;
import com.firebase.androidchat.adapter.ChatListAdapter;
import com.firebase.androidchat.adapter.CheckboxAdapter;
import com.firebase.androidchat.bean.Chat;
import com.firebase.androidchat.bean.User;
import com.firebase.client.*;
import drawing.BoardListActivity;
import dropbox.DropboxActivity;
import dropbox.UserActivity;
import io.agora.sample.agora.AgoraChannelActivity;

import java.util.ArrayList;
import java.util.HashMap;

public class ChatActivity extends AppCompatActivity {
    public static final String EXTRA_NEW_NAME = "new_name";
    public static final String EXTRA_HAS_CHANGED = "has_changed";
    private static final String[] STATE = {"Normal","Baned","Mute"};
    private static final String EXTRA_CHANNEL_FIREBASE_URL = "EXTRA_CHANNEL_FIREBASE_URL";

    private String mChannelName;
    private Firebase mFirebase;
    private Firebase mFirebaseChat;
    private ValueEventListener mConnectedListener;
    private ChatListAdapter mChatListAdapter;
    private String mUserName;
    private ArrayList<User> userList;
    private Firebase mFirebaseUser;
    private boolean isAdmin, isNewUser;

    private EditText inputText;

    public void setFirebaseChat(Firebase mFirebaseChat) {
        this.mFirebaseChat = mFirebaseChat;
    }

    public void setChatListAdapter(final ChatListAdapter mChatListAdapter) {
        this.mChatListAdapter = mChatListAdapter;
        final ListView listView = (ListView) findViewById(R.id.listview);
        listView.setAdapter(mChatListAdapter);
        mChatListAdapter.registerDataSetObserver(new DataSetObserver() {
            @Override
            public void onChanged() {
                super.onChanged();
                listView.setSelection(mChatListAdapter.getCount() - 1);
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        // Make sure we have a mChannelName
        setupChannelname();

        setupUsername();

        setTitle("Chatting in " + mChannelName);

        inputText = (EditText) findViewById(R.id.messageInput);

        // Setup our Firebase mFirebaseChat
        mFirebase = new Firebase(ChatApplication.FIREBASE_URL);
        mFirebaseChat = mFirebase.child("channel").child(mChannelName.replace(".", ",")).child("chat");
        mFirebaseUser = mFirebase.child("channel").child(mChannelName.replace(".", ",")).child("user");
        getUserList();

        // Setup our input methods. Enter key on the keyboard or pushing the send button
        EditText inputText = (EditText) findViewById(R.id.messageInput);
        inputText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {
                if (actionId == EditorInfo.IME_NULL && keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
                    sendMessage();
                }
                return true;
            }
        });

        findViewById(R.id.sendButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendMessage();
            }
        });
    }

    private void closeAndMessage() {
        Toast.makeText(this,"You have been baned by the admin",Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.chat_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_show_members:
                showMembers();
                return true;
            case R.id.action_map_location:
                Intent intent = new Intent(ChatActivity.this, MapsActivity.class);
                startActivity(intent);
                return true;
            case R.id.logout:
                backToLogin();
                return true;
            case R.id.action_draw:
                Intent i = new Intent(ChatActivity.this, BoardListActivity.class);
                startActivity(i);
                return true;
            case R.id.action_video:
                Intent toChannel = new Intent(ChatActivity.this, AgoraChannelActivity.class);
                toChannel.putExtra(AgoraChannelActivity.EXTRA_TYPE, AgoraChannelActivity.CALLING_TYPE_VIDEO);
                toChannel.putExtra(AgoraChannelActivity.EXTRA_CHANNEL, mChannelName);
                startActivity(toChannel);
                return true;
            case R.id.action_todo_list:
                Intent toTodoList = new Intent(ChatActivity.this, ToDoListActivity.class);
                toTodoList.putExtra(EXTRA_CHANNEL_FIREBASE_URL, mFirebase.child("channel").child(mChannelName.replace(".", ",")).toString());
                startActivity(toTodoList);
                return true;
            case R.id.action_share_file:
                Intent shareFile = new Intent(ChatActivity.this, UserActivity.class);
                startActivityForResult(shareFile, DropboxActivity.CREATE_SHARED_LINK_REQUEST);
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void backToLogin() {
        Intent intent = new Intent(getApplication(), LoginActivity.class);
        startActivity(intent);
        finish();
    }

    @Override
    public void onStart() {
        super.onStart();
        // Setup our view and list adapter. Ensure it scrolls to the bottom as data changes
        final ListView listView = (ListView) findViewById(R.id.listview);
        // Tell our list adapter that we only want 50 messages at a time
        mChatListAdapter = new ChatListAdapter(mFirebaseChat.limit(50), this, R.layout.chat_message, mUserName);
        listView.setAdapter(mChatListAdapter);
        mChatListAdapter.registerDataSetObserver(new DataSetObserver() {
            @Override
            public void onChanged() {
                super.onChanged();
                listView.setSelection(mChatListAdapter.getCount() - 1);
            }
        });

        // Finally, a little indication of connection status
        mConnectedListener = mFirebase.getRoot().child(".info/connected").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                boolean connected = (Boolean) dataSnapshot.getValue();
                if (connected) {
//                    Toast.makeText(ChatActivity.this, "Connected to Firebase", Toast.LENGTH_SHORT).show();
                } else {
//                    Toast.makeText(ChatActivity.this, "Disconnected from Firebase", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(FirebaseError firebaseError) {
                // No-op
            }
        });
    }

    @Override
    public void onResume(){
        super.onResume();
//        if(!userList.contains(mUserName)){
    }

    @Override
    public void onStop() {
        super.onStop();
        mFirebaseChat.getRoot().child(".info/connected").removeEventListener(mConnectedListener);
        mChatListAdapter.cleanup();
    }

    private void setupChannelname() {
        SharedPreferences prefs = getApplication().getSharedPreferences("ChatPrefs", 0);
        mChannelName = prefs.getString("channel", null);
    }

    private void setupUsername() {
        SharedPreferences prefs = getApplication().getSharedPreferences("ChatPrefs", 0);
        mUserName = prefs.getString("username", null);
    }

    private void userLoginAlertDialog(){
        LayoutInflater li = LayoutInflater.from(this);
        View promptsView = li.inflate(R.layout.username_alert_dialog, null);

        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(
                this);

        // set prompts.xml to alertdialog builder
        alertDialogBuilder.setView(promptsView);

        final EditText userName = (EditText) promptsView
                .findViewById(R.id.editTextDialogUserInput);
        if(mChannelName != null)
            userName.setText(mChannelName);

        final EditText userPassword = (EditText) promptsView
                .findViewById(R.id.editTextDialogUserPassword);

        // set dialog message
        alertDialogBuilder
                .setCancelable(false)
                .setPositiveButton("OK",
                        new DialogInterface.OnClickListener() {
                            public void onClick(final DialogInterface dialog, int id) {

                            }
                        })
                .setNegativeButton("Exit",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                backToLogin();
                            }
                        });

        // create alert dialog
        final AlertDialog alertDialog = alertDialogBuilder.create();

        // show it
        alertDialog.show();
        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String username = userName.getText().toString();
                String password = userPassword.getText().toString();
                mFirebase.authWithPassword(username, password, new Firebase.AuthResultHandler() {
                    @Override
                    public void onAuthenticated(AuthData authData) {
                        System.out.println("User ID: " + authData.getUid() + ", Provider: " + authData.getProvider());
                        alertDialog.dismiss();
                    }

                    @Override
                    public void onAuthenticationError(FirebaseError firebaseError) {
                        Toast.makeText(getBaseContext(), "User not exist or password wrong.", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });

    }

    private void sendMessage() {
        String input = inputText.getText().toString();
        if (!input.equals("")) {
            // Create our 'model', a Chat object
            Chat chat = new Chat(input, mUserName);
            // Create a new, auto-generated child of that chat location, and save our chat data there
            mFirebaseChat.push().setValue(chat);
            inputText.setText("");
            if(userList.size()==0){
                User user = new User(mUserName);
                user.setLevel(1);
                mFirebaseUser.child(mUserName.replace(".",",")).setValue(user);
            }else if(isNewUser){
                User user = new User(mUserName);
                mFirebaseUser.child(mUserName.replace(".",",")).setValue(user);
            }
        }
    }

    private void getUserList(){
        userList = new ArrayList<>();
        isNewUser = true;
        mFirebaseUser.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                User user = dataSnapshot.getValue(User.class);
                userList.add(user);
                if(user.getName().equalsIgnoreCase(mUserName)) {
                    isNewUser = false;
                    if (user.getLevel() == 1)
                        isAdmin = true;
                    if (user.getState() == 1)
                        closeAndMessage();
                }
            }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String s) {
                User user = dataSnapshot.getValue(User.class);
                if(user.getName().equalsIgnoreCase(mUserName)) {
                    if (user.getState() == 1)
                        closeAndMessage();
                }
            }

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {
                GenericTypeIndicator<HashMap<String, User>> t = new GenericTypeIndicator<HashMap<String, User>>() {
                };
                HashMap<String, User> map = dataSnapshot.getValue(t);
                if (map == null)
                    return;
                for (User u : map.values()) {
                    userList.remove(u);
                }
            }

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String s) {

            }

            @Override
            public void onCancelled(FirebaseError firebaseError) {
            }
        });
    }

    private void showMembers() {
        AlertDialog.Builder builderSingle = new AlertDialog.Builder(this);
        builderSingle.setIcon(R.drawable.ic_action_channel_members);
        builderSingle.setTitle(mChannelName);
        ArrayList<String> userNameList = new ArrayList<>();
        for(User u: userList){
            userNameList.add(u.getName());
        }
        final ArrayAdapter<User> arrayAdapter =
                new CheckboxAdapter(this,userList,mChannelName,isAdmin);
        builderSingle.setNegativeButton(
                "Close",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
        ;
        builderSingle.setAdapter(
                arrayAdapter
                , null
        );
        builderSingle.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == DropboxActivity.CREATE_SHARED_LINK_REQUEST
                && null != data) {
            inputText.setText(data.getStringExtra("SharedLink"));
            // sendMessage();
        }
    }


}