package com.acer.paroyalty;

public class ByteTools {

    public static String bytesToHex(byte[] in) {
        if (in == null) {
            return "";
        }
        final StringBuilder builder = new StringBuilder();
        for (byte b : in) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

    public static String bytesToHexWithSpaces(byte[] in) {
        final StringBuilder builder = new StringBuilder();
        for (byte b : in) {
            builder.append(String.format("%02x ", b));
        }
        return builder.toString().trim();
    }



    public static byte[] toByteArray(String hexString) {
        int len = hexString.length();
        byte[] bytes = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            bytes[i / 2] = (byte) ((Character.digit(hexString.charAt(i), 16) << 4)
                    + Character.digit(hexString.charAt(i + 1), 16));
        }
        return bytes;
    }

    public static int capToUnsignedShort(int value) {
        if (value > 65535) {
            return 65535;
        }
        else if (value < 0) {
            return 0;
        }
        else {
            return value;
        }
    }

    public static byte[] toShortInBytes_BE(int value) {
        byte[] data = new byte[2];
        data[0] = (byte)((value >> 8) & 0xFF);
        data[1] = (byte)((value     ) & 0xFF);
        return data;
    }

    public static int toIntFromShortInBytes_BE(byte[] data)  {
        return  ((data[0] & 0xFF) << 8) | ((data[1] & 0xFF));
    }

    public static byte[] convertDoubleToFixPoint(double value)
    {
        byte[] result = new byte[2];
        result[0] = (byte)value;
        result[1] = (byte)((value - Math.floor(value)) * 256);
        return result;
    }

}
