package com.lxdmp.proxypool;

import java.util.*;
import java.text.SimpleDateFormat;

public class App 
{
    public static void main(String[] args)
    {
		try{
			XiciCrawler crawler = new XiciCrawler(5);
			while(true)
			{
				crawler.update();
				Thread.sleep(10*1000);
			}
		}catch(Exception e){
			e.printStackTrace();
		}
    }
}

