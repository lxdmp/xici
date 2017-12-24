package com.lxdmp.proxypool;

import java.util.*;
import java.text.SimpleDateFormat;

public class Proxy
{
	private String ip;
	private int port;
	private String type; // "http"/"https"
	private String region;
	private Date lastValidateTime;
	private int speed; // [0, 100]

	public String formatUrl()
	{
		return String.format("%s://%s:%d", type, ip, port);
	}

	@Override
	public String toString()
	{
		SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		return String.format("%s://%s:%d %s %s %d", 
			this.type, this.ip, this.port, 
			this.region, f.format(this.lastValidateTime), this.speed);
	}

	@Override
	public int hashCode() 
	{
		return this.formatUrl().hashCode();
	}
	
	@Override
	public boolean equals(Object obj) 
	{
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Proxy other = (Proxy) obj;
		return (this.formatUrl().compareTo(other.formatUrl())==0);
	}

	String getIp()
	{
		return this.ip;
	}

	void setIp(String ip)
	{
		this.ip = ip;
	}

	int getPort()
	{
		return this.port;
	}

	void setPort(int port)
	{
		this.port = port;
	}

	String getType()
	{
		return this.type;
	}

	void setType(String type) throws Exception
	{
		if( type.compareTo("http")!=0 && 
			type.compareTo("https")!=0 )
			throw new Exception("Invalid type "+type);
		this.type = type;
	}

	String getRegion()
	{
		return this.region;
	}

	void setRegion(String region)
	{
		this.region = region;
	}

	Date getLastValidateTime()
	{
		return this.lastValidateTime;
	}

	void setLastValidateTime(Date stamp)
	{
		this.lastValidateTime = stamp;
	}

	void setLastValidateTime(int year, int month, int day, int hour, int min)
	{
		Calendar c = new GregorianCalendar();
		c.set(year, month-1, day, hour, min, 0); // month [0, 11]
		this.setLastValidateTime(c.getTime());
	}

	void setLastValidateTime(String s) throws Exception
	{
		String[] l = s.split(" ");
		if(l.length!=2)
			throw new Exception("Invalid time str "+s);
		String date_seg = l[0];
		String time_seg = l[1];

		String[] date_l = date_seg.split("-");
		if(date_l.length!=3)
			throw new Exception("Invalid time str "+s);
		int year = Integer.parseInt(date_l[0]);
		if(year<0 || year>99)
			throw new Exception("Invalid year "+s);
		year += 2000;
		int mon = Integer.parseInt(date_l[1]);
		int day = Integer.parseInt(date_l[2]);

		String[] time_l = time_seg.split(":");
		if(time_l.length!=2)
			throw new Exception("Invalid time str"+s);
		int hour = Integer.parseInt(time_l[0]);
		int min = Integer.parseInt(time_l[1]);

		this.setLastValidateTime(year, mon, day, hour, min);
	}

	int getSpeed()
	{
		return this.speed;
	}

	void setSpeed(int speed)
	{
		this.speed = speed;
		if(this.speed>100)
			this.speed = 100;
		else if(this.speed<0)
			this.speed = 0;
	}
}

