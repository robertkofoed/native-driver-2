package com.iboxendriverapp;

import java.util.Calendar;
import java.util.TimeZone;

public class QlocxHelpers {

    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    public byte[] addTimeToByteArray(byte[] response) {
        byte[] temp = new byte[response.length + 6];

        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        byte[] time = new byte[6];
        time[0] = (byte) (c.get(Calendar.YEAR) - 2000);
        time[1] = (byte) (c.get(Calendar.MONTH) + 1);
        time[2] = (byte) (c.get(Calendar.DAY_OF_MONTH));
        time[3] = (byte) (c.get(Calendar.HOUR_OF_DAY));
        time[4] = (byte) (c.get(Calendar.MINUTE));
        time[5] = (byte) (c.get(Calendar.SECOND));

        System.arraycopy(response, 0, temp, 0, response.length);
        System.arraycopy(time, 0, temp, response.length - 1, time.length);

        return temp;
    }

    private static final char[] HEX_ARRAY = "0123456789abcdef".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        if(bytes.length == 0) return "EMPTY!";

        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }
}
