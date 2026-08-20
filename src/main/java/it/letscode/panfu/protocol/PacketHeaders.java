package it.letscode.panfu.protocol;

public final class PacketHeaders {

    private PacketHeaders() {}

    public static final int LOGIN = 0;
    public static final int LOGOUT = 2;
    public static final int JOIN_GAME = 11;
    public static final int LEAVE_ROOM = 13;
    public static final int ENTER_MULTIGAME = 14;
    public static final int MULTIGAME = 15;
    public static final int QUIT_GAME = 16;
    public static final int ROTATE = 19;
    public static final int MOVE = 20;
    public static final int FORCE_COORDINATES = 21;
    public static final int JOIN_ROOM = 25;
    public static final int JOIN_HOME = 26;
    public static final int CHANGE_HOME_ROOM = 28;
    public static final int GET_ALL_HOUSES = 29;
    public static final int UPDATE_HOME_ROOM = 33;
    public static final int UPDATE_HOME_SOUND = 34;
    public static final int CHAT = 40;
    public static final int EMOTE = 41;
    public static final int SAFE_CHAT = 43;
    public static final int ACTION = 50;
    public static final int ADD_BUDDY = 60;
    public static final int GET_ROOM_ATTENDEES = 70;
    public static final int PLAYER_TO_PLAYER = 113;
    public static final int QUERY_SHARED_ITEMS = 140;
    public static final int SET_PLAYER_STATUS = 210;
    public static final int GET_PLAYER_IDS_BY_CLOTHES = 212;
    public static final int GET_SALT = 301;
    public static final int PING = 1050;

    public static final int LOGIN_RESPONSE = 0;
    public static final int DISCONNECT_RESPONSE = 2;
    public static final int ROOM_JOINED = 10;
    public static final int SINGLE_GAME_JOINED = 11;
    public static final int SUBROOM_ENTERED = 12;
    public static final int MULTIGAME_MESSAGE = 15;
    public static final int AVATAR_MOVED = 20;
    public static final int HOME_JOINED = 26;
    public static final int ALL_HOUSES = 29;
    public static final int SET_AVATAR = 30;
    public static final int UNSET_AVATAR = 31;
    public static final int PLAYER_INFO_UPDATED = 35;
    public static final int CHAT_MESSAGE = 40;
    public static final int EMOTE_MESSAGE = 41;
    public static final int SAFE_CHAT_TOGGLED = 45;
    public static final int ACTION_PERFORMED = 50;
    public static final int BUDDY_ADDED = 60;
    public static final int BUDDY_STATUS_UPDATED = 61;
    public static final int ROOM_ATTENDEES = 70;
    public static final int PLAYER_TO_PLAYER_RESPONSE = 113;
    public static final int SHARED_ITEMS = 140;
    public static final int PLAYER_STATUS = 210;
    public static final int PLAYER_IDS_BY_CLOTHES = 212;
    public static final int GAME_SERVER_MESSAGE = 260;
    public static final int SALT = 301;
}
