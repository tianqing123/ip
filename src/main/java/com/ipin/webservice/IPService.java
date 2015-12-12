package com.ipin.webservice;

import com.ipin.ipseeker.IPLocation;
import com.ipin.ipseeker.IPSeeker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.UnsupportedEncodingException;

/**
 * Created by jungui on 2015/11/23.
 */
@Controller
@RequestMapping("ip")
public class IPService {
    Logger logger = LoggerFactory.getLogger(this.getClass());
    @Autowired
    private IPSeeker ipSeeker;

    @RequestMapping("/ip_query/query")
    @ResponseBody
    public IPLocation query(String ip){
        logger.info("query "+ip);
        IPLocation location = ipSeeker.getIPLocation(ip);
        return location;
    }
}


