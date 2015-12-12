package com.ipin.ipseeker;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IPSeeker {

    private static final int IP_RECORD_LENGTH = 7;

    private static final byte REDIRECT_MODE_1 = 0x01;

    private static final byte REDIRECT_MODE_2 = 0x02;

    private static Logger logger = LoggerFactory.getLogger(IPSeeker.class);

    private static final Map<String, IPLocation> ipCacheMap = new HashMap<String, IPLocation>(100000);

    private RandomAccessFile ipFile;

    private MappedByteBuffer mbb;

    private int ipBegin;

    private int ipEnd;

    private byte[] tmpBuf;

    private byte[] tmpB4;

    private Map<String,Long> cityInfo = new HashMap<String, Long>();

    public void init(){
        tmpBuf = new byte[100];
        tmpB4 = new byte[4];

        try {
            String filepath = getClass().getClassLoader().getResource("qqwry.dat").getPath();
            ipFile = new RandomAccessFile(filepath, "r");
        } catch (IOException e) {
            logger.error("IP地址信息文件没有找到，IP显示功能将无法使用");
            return;
        }

        if (ipFile == null)
            return;

        // 读取文件头信息
        try {
            ipBegin = readInt4(0);
            ipEnd = readInt4(4);
            System.out.println(ipBegin+" "+ipEnd);
            if (ipBegin == -1 || ipEnd == -1) {
                ipFile.close();
                ipFile = null;
                return;
            }
        } catch (IOException e) {
            logger.error("IP地址信息文件格式有错误，IP显示功能将无法使用");
            ipFile = null;
            return;
        }

        // 映射IP信息文件到内存中
        try {
            FileChannel fc = ipFile.getChannel();
            long ipFileLen = ipFile.length();
            mbb = fc.map(FileChannel.MapMode.READ_ONLY, 0, ipFileLen);
            mbb.order(ByteOrder.LITTLE_ENDIAN);
            ipFile.close();

            logger.info("read ip file to memory, len = " + ipFileLen + " bytes");
        } catch (IOException e) {
            e.printStackTrace();
        }

        // city
        String citypath = getClass().getClassLoader().getResource("city_map.txt").getPath();
        File file = new File(citypath);
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(file), "utf-8"));

            while (br.ready()){
                String line =  br.readLine();
                String[] info = line.split(",");
                long cityCode = Long.valueOf(info[0]);
                String city = info[1];
                logger.info("city:" +city+" "+cityCode);
                cityInfo.put(city,cityCode);
            }
            logger.info("cityInfo size:" +cityInfo.size());
        } catch (FileNotFoundException e) {
            logger.error("读取city信息出错," + e.getMessage());
        } catch (IOException e) {
            logger.error("读取city信息出错," + e.getMessage());
        }
    }

    /**
     * 根据ip查归属地
     */
    public IPLocation getIPLocation(String ip) {
        byte[] ipBytes = getIpByteArrayFromString(ip); // 注：末位统一重置为0，以提高缓存命中率
        String ipSeg = getIpStringFromBytes(ipBytes); // 形如123.58.181.0的/24网段

        IPLocation loc = ipCacheMap.get(ipSeg);
        if (loc == null) {
            loc = getIPLocation(ipBytes);
            try{
                String city= loc.getCity();
                Pattern p1 = Pattern.compile("(?:省|区)(.*(?:市|州))");
                Pattern p2 = Pattern.compile("([^省区]+?市)$");

                Matcher m = p1.matcher(city);
                if(!m.find()) {
                    m = p2.matcher(city);
                    m.find();
                }
                city = m.group(1);
                loc.setCity(city);
                if(cityInfo.containsKey(city)){
                    loc.setCityCode(cityInfo.get(city));
                }
            }catch (Exception e){
                logger.error(e.getMessage());
            }finally {
                ipCacheMap.put(ipSeg, loc);
            }
        }
        return loc;
    }

    /**
     * 根据地点查ip区间
     */
    public List<IPEntry> getIPEntries(String s) {
        List<IPEntry> ret = new ArrayList<IPEntry>();

        int endOffset = ipEnd;
        for (int offset = ipBegin + 4; offset <= endOffset; offset += IP_RECORD_LENGTH) {
            int temp = readInt3(offset);
            if (temp != -1) {
                IPLocation ipLoc = getIPLocation(temp);
                // 判断是否这个地点里面包含了s子串，如果包含了，添加这个记录到List中，如果没有，继续
                if (ipLoc.getCity().indexOf(s) != -1
                    || ipLoc.getArea().indexOf(s) != -1) {
                    IPEntry entry = new IPEntry();
                    entry.setCity(ipLoc.getCity());
                    entry.setArea(ipLoc.getArea());
                    // 得到起始IP
                    readIP(offset - 4, tmpB4);
                    entry.setBeginIp(getIpStringFromBytes(tmpB4));
                    // 得到结束IP
                    readIP(temp, tmpB4);
                    entry.setEndIp(getIpStringFromBytes(tmpB4));
                    // 添加该记录
                    ret.add(entry);
                }
            }
        }
        return ret;
    }

    /**
     * 从内存映射文件的offset位置开始的3个字节读取一个int
     * 
     * @param offset
     * @return
     */
    private int readInt3(int offset) {
        mbb.position(offset);
        return mbb.getInt() & 0x00FFFFFF;
    }

    /**
     * 从内存映射文件的当前位置开始的3个字节读取一个int
     * 
     * @return
     */
    private int readInt3() {
        return mbb.getInt() & 0x00FFFFFF;
    }

    /**
     * 根据ip搜索ip信息文件，得到IPLocation结构，所搜索的ip参数从类成员ip中得到
     * 
     * @param ip
     *            要查询的IP
     * @return IPLocation结构
     */
    private IPLocation getIPLocation(byte[] ip) {
        IPLocation loc = null;
        int offset = locateIP(ip);
        if (offset != -1)
            loc = getIPLocation(offset);
        if (loc == null) {
            loc = new IPLocation();
            loc.setCity(IPLocation.UNKNOWN_COUNTRY);
            loc.setArea(IPLocation.UNKNOWN_AREA);
        }
        return loc;
    }

    /**
     * 从offset位置读取4个字节为一个long，因为java为big-endian格式，所以没办法 用了这么一个函数来做转换
     * 
     * @param offset
     * @return 读取的long值，返回-1表示读取文件失败
     */
    private int readInt4(int offset) {
        if (ipFile == null)
            return -1;

        int ret = 0;
        try {
            ipFile.seek(offset);
            ret |= (ipFile.readByte() & 0xFF);
            ret |= ((ipFile.readByte() << 8) & 0xFF00);
            ret |= ((ipFile.readByte() << 16) & 0xFF0000);
            ret |= ((ipFile.readByte() << 24) & 0xFF000000);
            return ret;
        } catch (IOException e) {
            return -1;
        }
    }

    /**
     * 从offset位置读取四个字节的ip地址放入ip数组中，读取后的ip为big-endian格式，但是
     * 文件中是little-endian形式，将会进行转换
     * 
     * @param offset
     * @param ip
     */
    private void readIP(int offset, byte[] ip) {
        mbb.position(offset);
        mbb.get(ip);
        byte temp = ip[0];
        ip[0] = ip[3];
        ip[3] = temp;
        temp = ip[1];
        ip[1] = ip[2];
        ip[2] = temp;
    }

    /**
     * 把类成员ip和beginIp比较，注意这个beginIp是big-endian的
     * 
     * @param ip
     *            要查询的IP
     * @param beginIp
     *            和被查询IP相比较的IP
     * @return 相等返回0，ip大于beginIp则返回1，小于返回-1。
     */
    private int compareIP(byte[] ip, byte[] beginIp) {
        for (int i = 0; i < 4; i++) {
            int r = compareByte(ip[i], beginIp[i]);
            if (r != 0)
                return r;
        }
        return 0;
    }

    /**
     * 把两个byte当作无符号数进行比较
     * 
     * @param b1
     * @param b2
     * @return 若b1大于b2则返回1，相等返回0，小于返回-1
     */
    private int compareByte(byte b1, byte b2) {
        if ((b1 & 0xFF) > (b2 & 0xFF)) // 比较是否大于
            return 1;
        else if ((b1 ^ b2) == 0)// 判断是否相等
            return 0;
        else
            return -1;
    }

    /**
     * 这个方法将根据ip的内容，定位到包含这个ip国家地区的记录处，返回一个绝对偏移 方法使用二分法查找。
     * 
     * @param ip
     *            要查询的IP
     * @return 如果找到了，返回结束IP的偏移，如果没有找到，返回-1
     */
    private int locateIP(byte[] ip) {
        int m = 0;
        int r;
        // 比较第一个ip项
        readIP(ipBegin, tmpB4);
        r = compareIP(ip, tmpB4);
        if (r == 0)
            return ipBegin;
        else if (r < 0)
            return -1;
        // 开始二分搜索
        for (int i = ipBegin, j = ipEnd; i < j;) {
            m = getMiddleOffset(i, j);
            readIP(m, tmpB4);
            r = compareIP(ip, tmpB4);
            // log.debug(Utils.getIpStringFromBytes(b));
            if (r > 0)
                i = m;
            else if (r < 0) {
                if (m == j) {
                    j -= IP_RECORD_LENGTH;
                    m = j;
                } else
                    j = m;
            } else
                return readInt3(m + 4);
        }
        // 如果循环结束了，那么i和j必定是相等的，这个记录为最可能的记录，但是并非
        // 肯定就是，还要检查一下，如果是，就返回结束地址区的绝对偏移
        m = readInt3(m + 4);
        readIP(m, tmpB4);
        r = compareIP(ip, tmpB4);
        if (r <= 0)
            return m;
        else
            return -1;
    }

    /**
     * 得到begin偏移和end偏移中间位置记录的偏移
     * 
     * @param begin
     * @param end
     * @return
     */
    private int getMiddleOffset(int begin, int end) {
        int records = (end - begin) / IP_RECORD_LENGTH;
        records >>= 1;
        if (records == 0)
            records = 1;
        return begin + records * IP_RECORD_LENGTH;
    }

    /**
     * 给定一个ip国家地区记录的偏移，返回一个IPLocation结构，此方法应用与内存映射文件方式
     * 
     * @param offset
     *            国家记录的起始偏移
     * @return IPLocation对象
     */
    private IPLocation getIPLocation(int offset) {
        IPLocation loc = new IPLocation();
        // 跳过4字节ip
        mbb.position(offset + 4);
        // 读取第一个字节判断是否标志字节
        byte b = mbb.get();
        if (b == REDIRECT_MODE_1) {
            // 读取国家偏移
            int countryOffset = readInt3();
            // 跳转至偏移处
            mbb.position(countryOffset);
            // 再检查一次标志字节，因为这个时候这个地方仍然可能是个重定向
            b = mbb.get();
            if (b == REDIRECT_MODE_2) {
                loc.setCity(readString(readInt3()));
                mbb.position(countryOffset + 4);
            } else
                loc.setCity(readString(countryOffset));
            // 读取地区标志
            loc.setArea(readArea(mbb.position()));
        } else if (b == REDIRECT_MODE_2) {
            loc.setCity(readString(readInt3()));
            loc.setArea(readArea(offset + 8));
        } else {
            loc.setCity(readString(mbb.position() - 1));
            loc.setArea(readArea(mbb.position()));
        }
        return loc;
    }

    /**
     * @param offset
     *            地区记录的起始偏移
     * @return 地区名字符串
     */
    private String readArea(int offset) {
        mbb.position(offset);
        byte b = mbb.get();
        if (b == REDIRECT_MODE_1 || b == REDIRECT_MODE_2) {
            int areaOffset = readInt3();
            if (areaOffset == 0)
                return IPLocation.UNKNOWN_AREA;
            else
                return readString(areaOffset);
        } else
            return readString(offset);
    }

    /**
     * 从内存映射文件的offset位置得到一个0结尾字符串
     * 
     * @param offset
     *            字符串起始偏移
     * @return 读取的字符串，出错返回空字符串
     */
    private String readString(int offset) {
        try {
            mbb.position(offset);
            int i;
            for (i = 0, tmpBuf[i] = mbb.get(); tmpBuf[i] != 0; tmpBuf[++i] = mbb
                .get());
            if (i != 0)
                return getString(tmpBuf, 0, i, "GBK");
        } catch (IllegalArgumentException e) {
            logger.error(e.getMessage());
        }
        return "";
    }

    /**
     * @param ip
     *            ip的字节数组形式
     * @return 字符串形式的ip
     */
    private static String getIpStringFromBytes(byte[] ip) {
        StringBuilder sb = new StringBuilder();
        sb.append(ip[0] & 0xFF);
        sb.append('.');
        sb.append(ip[1] & 0xFF);
        sb.append('.');
        sb.append(ip[2] & 0xFF);
        sb.append('.');
        sb.append(ip[3] & 0xFF);
        return sb.toString();
    }

    /**
     * 从ip的字符串形式得到字节数组形式
     * 
     * @param ip
     *            字符串形式的ip
     * @return 字节数组形式的ip
     */
    private static byte[] getIpByteArrayFromString(String ip) {
        byte[] ret = new byte[4];
        StringTokenizer st = new StringTokenizer(ip, ".");
        try {
            ret[0] = (byte) (Integer.parseInt(st.nextToken()) & 0xFF);
            ret[1] = (byte) (Integer.parseInt(st.nextToken()) & 0xFF);
            ret[2] = (byte) (Integer.parseInt(st.nextToken()) & 0xFF);
            ret[3] = 0; // [!] 末位统一置0，相同/24网段的ip视为同一个，提高缓存命中率
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
        return ret;
    }

    /**
     * 根据某种编码方式将字节数组转换成字符串
     * 
     * @param b
     *            字节数组
     * @param offset
     *            要转换的起始位置
     * @param len
     *            要转换的长度
     * @param encoding
     *            编码方式
     * @return 如果encoding不支持，返回一个缺省编码的字符串
     */
    private static String getString(byte[] b, int offset, int len,
        String encoding) {
        try {
            return new String(b, offset, len, encoding);
        } catch (UnsupportedEncodingException e) {
            return new String(b, offset, len);
        }
    }
}
