package it.letscode.panfu.protocol;

public final class P2pHeaders {

    private P2pHeaders() {}

    public static final int RECEIVER_ALL = -1;
    public static final int RECEIVER_ROOM = -2;
    public static final int CREATE_AVATAR = 10;
    public static final int UPDATE_AVATAR = 11;
    public static final int USE_SHARED_ITEM = 12;
    public static final int SHOW_STATUS = 14;
    public static final int HIDE_STATUS = 15;
    public static final int REPLAY_AVATAR_ACTION = 21;
}
