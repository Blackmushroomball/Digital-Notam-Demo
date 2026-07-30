package com.example.aixm.geometry.json;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses WGS-84 coordinates supplied either as decimal degrees or as DMS.
 *
 * <p>Accepted DMS examples include {@code 53°37'11.25"N},
 * {@code N533711.25} and {@code 53 37 11.25 N}. Longitude uses E/W and
 * latitude uses N/S. The returned value is always signed decimal degrees.</p>
 */
public final class CoordinateParser {
    private static final Pattern HEMISPHERE = Pattern.compile("[NSEW]");

    private CoordinateParser() {}

    public static BigDecimal parse(String source, Axis axis) {
        if (source == null || source.isBlank()) throw new IllegalArgumentException("坐标不能为空");
        String value=source.trim().toUpperCase(Locale.ROOT);
        Matcher matcher=HEMISPHERE.matcher(value);Character hemisphere=null;
        while(matcher.find()){
            char found=matcher.group().charAt(0);
            if(hemisphere!=null&&hemisphere!=found)throw new IllegalArgumentException("坐标包含冲突的半球标识");
            hemisphere=found;
        }
        if(hemisphere!=null&&!axis.accepts(hemisphere))
            throw new IllegalArgumentException(axis==Axis.LONGITUDE?"经度只能使用E/W":"纬度只能使用N/S");
        value=HEMISPHERE.matcher(value).replaceAll("").trim()
                .replace('°',' ').replace('º',' ').replace('′',' ')
                .replace('’',' ').replace('\'',' ').replace('″',' ')
                .replace('”',' ').replace('"',' ').replaceAll("\\s+"," ").trim();
        boolean negative=value.startsWith("-");
        String unsigned=(value.startsWith("+")||negative)?value.substring(1):value;
        double degrees;
        String[] parts=unsigned.split(" ");
        if(parts.length==1&&compactDms(parts[0],axis,hemisphere!=null)){
            int degreeDigits=axis==Axis.LATITUDE?2:3;
            degrees=Double.parseDouble(parts[0].substring(0,degreeDigits))
                    +Double.parseDouble(parts[0].substring(degreeDigits,degreeDigits+2))/60d
                    +Double.parseDouble(parts[0].substring(degreeDigits+2))/3600d;
        }else if(parts.length==1){
            degrees=Double.parseDouble(parts[0]);
        }else if(parts.length==2||parts.length==3){
            double minutes=Double.parseDouble(parts[1]),seconds=parts.length==3?Double.parseDouble(parts[2]):0;
            if(minutes<0||minutes>=60||seconds<0||seconds>=60)throw new IllegalArgumentException("分和秒必须在0到60之间");
            degrees=Double.parseDouble(parts[0])+minutes/60d+seconds/3600d;
        }else throw new IllegalArgumentException("无法识别坐标格式");
        if(hemisphere!=null){
            boolean hemisphereNegative=hemisphere=='W'||hemisphere=='S';
            if(negative&&!hemisphereNegative)throw new IllegalArgumentException("负号与半球标识冲突");
            negative=hemisphereNegative;
        }
        degrees=negative?-degrees:degrees;
        double limit=axis==Axis.LATITUDE?90:180;
        if(!Double.isFinite(degrees)||Math.abs(degrees)>limit)
            throw new IllegalArgumentException((axis==Axis.LATITUDE?"纬度":"经度")+"超出范围");
        return BigDecimal.valueOf(degrees);
    }

    private static boolean compactDms(String value,Axis axis,boolean hasHemisphere){
        if(!hasHemisphere||!value.matches("\\d+(?:\\.\\d+)?"))return false;
        int integerDigits=value.indexOf('.')<0?value.length():value.indexOf('.');
        return integerDigits==(axis==Axis.LATITUDE?6:7);
    }

    public enum Axis {
        LONGITUDE, LATITUDE;
        boolean accepts(char hemisphere){
            return this==LONGITUDE?(hemisphere=='E'||hemisphere=='W'):(hemisphere=='N'||hemisphere=='S');
        }
    }
}
