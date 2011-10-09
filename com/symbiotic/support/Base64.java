package com.symbiotic.support;

import java.io.IOException;

/**
 * Base64 class to encode and decode data.
 * Created because android.util.Base64 is level 8+
 */
public final class Base64
{
	private static char mapValue(int value)
	{
		if(value < 26)
			return (char)(value + 65);
		else if(value < 52)
			return (char)((value - 26) + 97);
		else if(value < 62)
			return (char)((value - 52) + 48);
		else if(value == 62)
			return '+';
		else
			return '/';
	}
	
	private static String mapBits(byte []bits)
	{
		StringBuilder stringBuilder = new StringBuilder();
		int i, value = 0;
		
		for(i = 0; i < bits.length; ++i)
			value = (value << 8) | ((int)bits[i] & 0xFF);
				
		if(bits.length == 2)
			value = (value << 2);
		else if(bits.length == 1)
			value = (value << 4);
		
		for(i = bits.length * 6; i >= 0; i -= 6)
			stringBuilder.append(mapValue((value >> i) & 0x3F));
		
		if(bits.length == 2)
			stringBuilder.append('=');
		else if(bits.length == 1)
			stringBuilder.append("==");
		
		return stringBuilder.toString();
	}
	
	private static int mapChar(char c) throws IOException
	{
		if((c >= 'A') && (c <= 'Z'))
			return c - 'A';
		else if((c >= 'a') && (c <= 'z'))
			return c - 'a' + 26;
		else if((c >= '0') && (c <= '9'))
			return c - '0' + 52;
		else if(c == '+')
			return 62;
		else if(c == '/')
			return 63;
		else
			throw new IOException();
	}
	
	private static byte []mapChars(String substr) throws IOException
	{
		int i, value = 0;
		byte []bits;
		
		for(i = 0; i < substr.length(); ++i)
		{
			if(substr.charAt(i) == '=')
				break;
			value = (value << 6) | mapChar(substr.charAt(i));
		}
		
		if(i == 2)
		{
			bits = new byte[1];
			bits[0] = (byte)((value >> 4) & 0xFF);
		}
		else if(i == 3)
		{
			bits = new byte[2];
			bits[1] = (byte)((value >> 2) & 0xFF);
			bits[0] = (byte)((value >> 10) & 0xFF);
		}
		else
		{
			bits = new byte[3];
			bits[2] = (byte)(value & 0xFF);
			bits[1] = (byte)((value >> 8) & 0xFF);
			bits[0] = (byte)((value >> 16) & 0xFF);
		}
		
		return bits;
	}
	
	public static String encode(byte []data)
	{
		StringBuilder encodedString = new StringBuilder();
		byte []bits = new byte[3];
		for(int i = 0; i < (data.length - (data.length % 3)); i += 3)
		{
			bits[0] = data[i];
			bits[1] = data[i + 1];
			bits[2] = data[i + 2];
			encodedString.append(mapBits(bits));
		}
		
		if((data.length % 3) == 1)
			encodedString.append(mapBits(new byte[] { data[data.length - 1] }));
		else if((data.length % 3) == 2)
			encodedString.append(mapBits(new byte[] { data[data.length - 2], data[data.length - 1] }));
		
		return encodedString.toString();
	}
	
	public static byte []decode(String base64Str) throws IOException
	{
		int size;
		
		if((base64Str.length() % 4) != 0)
			throw new IOException();
	
		size = (base64Str.length()/4) * 3;
		if(base64Str.charAt(base64Str.length() - 2) == '=')
			size -= 2;
		else if(base64Str.charAt(base64Str.length() - 1) == '=')
			size -= 1;
	
		byte []bytes = new byte[size];
		byte []bits;
		
		for(int i = 0; (i * 4) < base64Str.length(); ++i)
		{
			bits = mapChars(base64Str.substring((i * 4), (i * 4) + 4));
			for(int j = 0; j < bits.length; ++j)
			{
				bytes[(i * 3) + j] = bits[j];
			}
		}
		return bytes;
	}
}
