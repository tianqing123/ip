package com.ipin.ipseeker;

/**
 * 用来封装ip相关信息
 *
 */
public class IPLocation {

    public static final String UNKNOWN_COUNTRY = "unknown_country";

    public static final String UNKNOWN_AREA = "unknown_area";

    private String country;

    private String city;

    private long cityCode;

    private String area;

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public long getCityCode() {
        return cityCode;
    }

    public void setCityCode(long cityCode) {
        this.cityCode = cityCode;
    }

    public String getArea() {
        return area;
    }

    public void setArea(String area) {
        this.area = area;
    }
}
