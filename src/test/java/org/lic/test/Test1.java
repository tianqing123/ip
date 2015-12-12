package org.lic.test;

import com.ipin.ipseeker.IPLocation;
import org.junit.Test;
import com.ipin.ipseeker.IPSeeker;

/**
 * Created by lc on 14-10-9.
 */
public class Test1 {

    private static final IPSeeker ipSeeker = new IPSeeker();  // 纯真


    @Test
    public void test() throws Exception {
        ipSeeker.init();
        String ip = "114.114.114.114";
        IPLocation ipLocation = ipSeeker.getIPLocation(ip);
        System.out.println(ipLocation.getCity());
    }
}
