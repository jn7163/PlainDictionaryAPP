/*  Copyright 2018 KnIfER Zenjio-Kang

	Licensed under the Apache License, Version 2.0 (the "License");
	you may not use this file except in compliance with the License.
	You may obtain a copy of the License at
	
	    http://www.apache.org/licenses/LICENSE-2.0
	
	Unless required by applicable law or agreed to in writing, software
	distributed under the License is distributed on an "AS IS" BASIS,
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	See the License for the specific language governing permissions and
	limitations under the License.
	
	Mdict-Java Query Library
*/

package com.knziha.plod.dictionary.Utils;

import java.io.*;
import java.nio.charset.Charset;
import java.util.zip.Adler32;
import java.util.zip.InflaterOutputStream;


/**
 * @author KnIfER
 * @date 2018/05/31
 */
public class  BU{//byteUtils

	public static int calcChecksum(byte[] bytes) {
        Adler32 a32 = new Adler32();
        a32.update(bytes);
		return (int) a32.getValue();
    }
	public static int calcChecksum(byte[] bytes,int off,int len) {
        Adler32 a32 = new Adler32();
        a32.update(bytes,off,len);
		return (int) a32.getValue();
    }
    //解压等utils
    @Deprecated
    public static byte[] zlib_decompress(byte[] encdata,int offset,int ln) {
	    try {
			    ByteArrayOutputStream out = new ByteArrayOutputStream();
			    InflaterOutputStream inf = new InflaterOutputStream(out); 
			    inf.write(encdata,offset, ln); 
			    inf.close(); 
			    return out.toByteArray(); 
		    } catch (Exception ex) {
		    	ex.printStackTrace(); 
		    	return "ERR".getBytes(); 
		    }
    }

    @Deprecated
    public static byte[] toLH(int n) {  
    	  byte[] b = new byte[4];  
    	  b[0] = (byte) (n & 0xff);  
    	  b[1] = (byte) (n >> 8 & 0xff);  
    	  b[2] = (byte) (n >> 16 & 0xff);  
    	  b[3] = (byte) (n >> 24 & 0xff);  
    	  return b;  
    	} 
    
    
    
    public static long toLong(byte[] buffer,int offset) {   
        long  values = 0;   
        for (int i = 0; i < 8; i++) {    
            values <<= 8; values|= (buffer[offset+i] & 0xff);   
        }   
        return values;  
     } 
    public static int toInt(byte[] buffer,int offset) {   
        int  values = 0;   
        for (int i = 0; i < 4; i++) {    
            values <<= 8; values|= (buffer[offset+i] & 0xff);   
        }   
        return values;  
     }     
    
    
	static byte[] _fast_decrypt(byte[] data,byte[] key){ 
	    long previous = 0x36;
	    for(int i=0;i<data.length;i++){
	    	//INCONGRUENT CONVERTION FROM byte to int
	    	int ddd = data[i]&0xff;
	    	long t = (ddd >> 4 | ddd << 4) & 0xff;
	        t = t ^ previous ^ (i & 0xff) ^ (key[(i % key.length)]&0xff);
	        previous = ddd;
	        data[i] = (byte) t;
        }
	    return data;
    }
	
	public static byte[] _mdx_decrypt(byte[] comp_block) throws IOException{
		ByteArrayOutputStream data = new ByteArrayOutputStream() ;
		data.write(comp_block,4,4);
		data.write(ripemd128.packIntLE(0x3695));
	    byte[]  key = ripemd128.ripemd128(data.toByteArray());
	    data.reset();
	    data.write(comp_block,0,8);
	    byte[] comp_block2 = new byte[comp_block.length-8];
	    System.arraycopy(comp_block, 8, comp_block2, 0, comp_block.length-8);
	    data.write(_fast_decrypt(comp_block2, key));
	    return data.toByteArray();
    }


    @Deprecated
	public static void printBytes2(byte[] b) {
		for(int i=0;i<b.length;i++)
    		System.out.print((int)(b[i]&0xff)+",");
    	System.out.println();
	}
    @Deprecated
    public static void printBytes(byte[] b){
    	for(int i=0;i<b.length;i++)
    		System.out.print("0x"+byteTo16(b[i])+",");
    	System.out.println();
    }
    @Deprecated
    public static void printBytes(byte[] b,int off,int ln){
    	for(int i=off;i<off+ln;i++)
    		System.out.print("0x"+byteTo16(b[i])+",");
    	System.out.println();
    }
    @Deprecated
    public static void printFile(byte[] b,int off,int ln,File path){
    	try {
			File p = path.getParentFile();
			if(!p.exists()) p.mkdirs();
			FileOutputStream fo = new FileOutputStream(path);
			fo.write(b);
			fo.flush();
			fo.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
    }
    @Deprecated
    public static void printFile(byte[] b, String path){
		printFile(b,0,b.length,new File(path));
    }

    @Deprecated
    public static void printFile(byte[] b, File path){
		printFile(b,0,b.length,path);
    }

    @Deprecated
    public static void printFileStream(InputStream b, File path){
		try {
			printStreamToFile(b,0,b.available(),path);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

    @Deprecated
    public static void printStreamToFile(InputStream b, int start, int end,  File path){
		try {
			if(start>0)
				b.skip(start);
			File p = path.getParentFile();
			if(!p.exists()) p.mkdirs();
			FileOutputStream fo = new FileOutputStream(path);
			byte[] data = new byte[4096];
			int len;
			while ((len=b.read(data))>0){
				fo.write(data, 0, len);
			}
			fo.flush();
			fo.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
    }

    public static String byteTo16(byte bt){
        String[] strHex={"0","1","2","3","4","5","6","7","8","9","a","b","c","d","e","f"};
        String resStr="";
        int low =(bt & 15);
        int high = bt>>4 & 15;
        resStr = strHex[high]+strHex[low];
        return resStr;
    }
    
    
    
    

    public static char toChar(byte[] buffer,int offset) {   
        char  values = 0;   
        for (int i = 0; i < 2; i++) {    
            values <<= 8; values|= (buffer[offset+i] & 0xff);   
        }   
        return values;  
    }


	public static ByteArrayInputStream fileToBytes(File f) {
		return new ByteArrayInputStream(fileToByteArr(f));
	}

	public static byte[] fileToByteArr(File f) {
		try {
			FileInputStream fin = new FileInputStream(f);
			byte[] data = new byte[(int) f.length()];
			fin.read(data);
			return data;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	public static String fileToString(File f) {
		try {
			FileInputStream fin = new FileInputStream(f);
			byte[] data = new byte[(int) f.length()];
			fin.read(data);
			return new String(data, "utf8");
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}


	public static String fileToString(String path, byte[] buffer, ReusableByteOutputStream bo, Charset charset) {
		try {
			FileInputStream fin = new FileInputStream(path);
			bo.reset();
			int len;
			while((len = fin.read(buffer))>0)
				bo.write(buffer, 0, len);
			return new String(bo.data(),0, bo.size(), charset);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}


	@Deprecated
    public long toLong1(byte[] b,int offset)
	{
		long l = 0;
		l = b[offset+0];
		l |= ((long) b[offset+1] << 8);
		l |= ((long) b[offset+2] << 16);
		l |= ((long) b[offset+3] << 24);
		l |= ((long) b[offset+4] << 32);
		l |= ((long) b[offset+5] << 40);
		l |= ((long) b[offset+6] << 48);
		l |= ((long) b[offset+7] << 56);
		return l;
	}
	
    
    public static String unwrapMdxName(String in) {
    	if(in.toLowerCase().endsWith(".mdx"))
    		return in.substring(0,in.length()-4);
    	return in;
    }
    public static String unwrapMddName(String in) {
    	if(in.toLowerCase().endsWith(".mdd"))
    		return in.substring(0,in.length()-4);
    	return in;
    }
}
	


