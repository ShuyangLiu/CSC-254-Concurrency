/*
 * A Helper class that can make the texts displayed in colors
 * Just trying to make it easier to see in console and for also fun
 * */

public class Color_Code {
	
	//(char)27 is the ESC character
	
	// Some Pre-defined color code (High contrast)
	public static final String RESET = (char)27+"[0m";
	public static final String BLACK = (char)27+"[90m";
	public static final String RED = (char)27+"[91m";
	public static final String GREEN = (char)27+"[92m";
	public static final String YELLOW = (char)27+"[93m";
	public static final String BLUE = (char)27+"[94m";
	public static final String PURPLE = (char)27+"[95m";
	public static final String CYAN = (char)27+"[96m";
	public static final String WHITE = (char)27+"[97m";
	
	public static String wrap (String str, String code) {
		return code+str+RESET;
	}
	
	//0 <= r, g, b <=5
	public static String wrap(String str, int r, int g, int b) {
		return (char)27+"[38;5;"+(16+36*r+6*g+b)+"m"+str+RESET;
	}
	
	public static String wrap(String str, int code) {
		return (char)27+"[38;5;"+code+"m"+str+RESET;
	}
	
	public static String wrap(String str, int foreground, int background) {
		return (char)27+"[48;5;"+background+"m"+(char)27+"[38;5;"+foreground+"m"+str+RESET;
	}
	
	public static String bold(String str){
		return (char)27+"[1m"+str;
	}

}