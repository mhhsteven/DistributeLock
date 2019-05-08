package com.mzy.distribute.controller;

import com.mzy.distribute.vo.PurchaseVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.util.Random;

@Controller
@RequestMapping("secondKill")
public class SecondKillController {

    private static final Logger LOGGER = LoggerFactory.getLogger(SecondKillController.class);

    @RequestMapping(value = "purchase", method = RequestMethod.POST)
    @ResponseBody
    public String purchase(HttpServletRequest request, PurchaseVO purchaseVO) {
        LOGGER.info("request param: {}", purchaseVO);
        long before = System.currentTimeMillis();
        Random random = new Random();
        try {
            long delay = 1000 + random.nextInt(4000);
            Thread.sleep(delay);
        } catch (Exception e) {

        }
        long after = System.currentTimeMillis();
        System.out.println("耗时(" + (after - before) + "ms), 响应: " + purchaseVO);
        return purchaseVO.toString();
    }
}
