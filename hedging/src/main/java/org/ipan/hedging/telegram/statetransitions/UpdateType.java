package org.ipan.hedging.telegram.statetransitions;

public class UpdateType {
    public static final int NONE = 0;
    public static final int MESSAGE = 1;
    public static final int EDITED_MESSAGE = 2;
    public static final int CALLBACK_QUERY = 4;
    public static final int INLINE_QUERY = 8;
}