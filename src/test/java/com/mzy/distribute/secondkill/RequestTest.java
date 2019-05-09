package com.mzy.distribute.secondkill;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CountDownLatch;

public class RequestTest {

    private CountDownLatch lanch = new CountDownLatch(1000000);

    @Test
    public void test() throws Exception {
        for (int i = 0; i < 100; i++) {
            Thread t = new Thread(() -> {
                for (int j = 0; j < 100; j++) {
                    Thread h = new Thread(() -> {
                        for (int k = 0; k < 100; k++) {
                            Thread r = new Thread(() -> {
                                request();
                            });
                            r.start();
                        }
                    });
                    h.start();
                }
            });
            t.start();
        }
        lanch.await();
    }

    private void request() {
        long before = System.currentTimeMillis();
        String response = this.httpRequest("http://127.0.0.1:8088/distribute/secondKill/purchase?productId=2", null);
        long after = System.currentTimeMillis();
        System.out.println("耗时(" + (after - before) + "ms), 响应: " + response);
        lanch.countDown();
    }

    private String httpRequest(String url, Object object) {
        try {
            URL requestUrl = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) requestUrl.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("accept", "application/json");
            connection.setRequestProperty("Source-Type", "1");
            connection.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
            connection.setRequestProperty("connection", "Keep-Alive");
            connection.setRequestProperty("user-agent", "Mozilla/5.0 (compatible; MSIE 6.0; Windows NT 5.1;SV1)");
            connection.setDoOutput(true);
            connection.setDoInput(true);
            PrintWriter out = new PrintWriter(connection.getOutputStream());
            out.print(object);
            out.flush();
            out.close();

            BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String temp = null;
            while ((temp = br.readLine()) != null) {
                sb.append(temp);
            }
            return sb.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
