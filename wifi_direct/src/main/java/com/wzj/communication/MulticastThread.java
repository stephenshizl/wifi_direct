package com.wzj.communication;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.wzj.bean.Member;
import com.wzj.util.AddressObtain;
import com.wzj.wifi_direct.WiFiDirectActivity;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by wzj on 2017/3/22.
 */

public class MulticastThread implements Runnable {
    public static final String TAG = "MulticastThread";
    public static final char GO_MULTICAST_MEMMAP = '0';
    public static final char GM_MULTICAST_DETECT = '1';
    public static final char GO_MULTICAST_DELETE = '2';
    public static final char GO_MULTICAST_MEMMAP_FORWARD = '3';
    public static final char GO_MULTICAST_DELETE_FORWARD = '4';
    public static final char MULTICAST_WRITE = '0';
    public static final char MULTICAST_READ = '1';
    public static final String P2P_INTERFACE_REGREX = "^p2p";
    public static final String WLAN_INTERFACE_REGREX = "^wlan";
    private static final String MULTICAST_IP = "239.0.0.1";
    public static final int MULTICSAT_PORT = 30000;
    private static final int DATA_LEN = 1472;
    private MulticastSocket multicastSocket = null;
    private InetAddress multicastAddress = null;
    private byte[] buf = new byte[DATA_LEN];
    private DatagramPacket inPacket = new DatagramPacket(buf, buf.length);
    private DatagramPacket outPacket = null;
    private char type = '0';
    private char messageType;
    private Handler mHandler;
    private NetworkInterface networkInterface = null;
    private String readInterfaceRegrex = P2P_INTERFACE_REGREX;
    private String writeInterfaceRegrex = P2P_INTERFACE_REGREX;
    private Map<String, Member> memberMap;
    private String macAddress;
    private String macOfP2P;
    private String otherGOMAC;
    private WiFiDirectActivity wiFiDirectActivity;
    private Member delMember;
    private char interfaceType;

    //Multicast读
    public MulticastThread(WiFiDirectActivity wiFiDirectActivity, char type, Handler mHandler, String readInterfaceRegrex) {
        this.wiFiDirectActivity = wiFiDirectActivity;
        this.type = type;
        this.mHandler = mHandler;
        this.readInterfaceRegrex = readInterfaceRegrex;
    }

    //Multicast写
    public MulticastThread(WiFiDirectActivity wiFiDirectActivity, char type, char messageType, String writeInterfaceRegrex, Map<String, Member> memberMap, String macOfP2P) {
        this.wiFiDirectActivity = wiFiDirectActivity;
        this.type = type;
        this.messageType = messageType;
        this.writeInterfaceRegrex = writeInterfaceRegrex;
        this.memberMap = memberMap;
        this.macOfP2P = macOfP2P;
    }
    //GO_MULTICAST_MEMMAP_FORWARD写
    public MulticastThread(WiFiDirectActivity wiFiDirectActivity, char type, char messageType, String otherGOMAC, String writeInterfaceRegrex, Map<String, Member> memberMap) {
        this.wiFiDirectActivity = wiFiDirectActivity;
        this.type = type;
        this.messageType = messageType;
        this.writeInterfaceRegrex = writeInterfaceRegrex;
        this.memberMap = memberMap;
        this.otherGOMAC = otherGOMAC;
    }
    //GM_DETECT Multicast写
    public MulticastThread(WiFiDirectActivity wiFiDirectActivity, char type, char messageType, String writeInterfaceRegrex) {
        this.wiFiDirectActivity = wiFiDirectActivity;
        this.type = type;
        this.messageType = messageType;
        this.writeInterfaceRegrex = writeInterfaceRegrex;

    }
    //GO_DELETE Multicast写
    public MulticastThread(WiFiDirectActivity wiFiDirectActivity, char type, char messageType, String writeInterfaceRegrex, Member delMember) {
        this.wiFiDirectActivity = wiFiDirectActivity;
        this.type = type;
        this.messageType = messageType;
        this.writeInterfaceRegrex = writeInterfaceRegrex;
        this.delMember = delMember;
    }
    //GO_DELETE_FORWARD写
    public MulticastThread(WiFiDirectActivity wiFiDirectActivity, char type, char messageType, String writeInterfaceRegrex, Member delMember, String otherGOMAC) {
        this.wiFiDirectActivity = wiFiDirectActivity;
        this.type = type;
        this.messageType = messageType;
        this.writeInterfaceRegrex = writeInterfaceRegrex;
        this.delMember = delMember;
        this.otherGOMAC = otherGOMAC;
    }
    @Override
    public void run() {

        //System.out.println("看这里 "+multicastSocket.getInterface().getHostName());
        if(type == MULTICAST_WRITE){
            try {
                if(multicastSocket == null || multicastSocket.isClosed()){
                    multicastAddress = InetAddress.getByName(MULTICAST_IP);
                    multicastSocket = new MulticastSocket();
                    multicastSocket.setLoopbackMode(true);
                    Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
                    String regrex = writeInterfaceRegrex;
                    Pattern pattern = Pattern.compile(regrex);
                    while(networkInterfaces.hasMoreElements()){
                        NetworkInterface networkInterface1 = networkInterfaces.nextElement();
                        Matcher matcher = pattern.matcher(networkInterface1.getName());
                        if(matcher.find()){
                            networkInterface = networkInterface1;
                            macAddress = getMacFromBytes(networkInterface.getHardwareAddress());
                            Log.d("NetworkInterface", "匹配/"+networkInterface.getName()+"/"+ macAddress);
                            break;
                        }
                    }
                    if(networkInterface != null && networkInterface.getInetAddresses() != null){
                        //Specify the network interface for outgoing multicast datagrams sent on this socket.
                        if(writeInterfaceRegrex.equals(WLAN_INTERFACE_REGREX)){
                            if(wiFiDirectActivity.getWiFiBroadcastReceiver().isWFDConnected()){
                                multicastSocket.setNetworkInterface(networkInterface);
                                //multicastSocket.joinGroup(new InetSocketAddress(multicastAddress, MULTICSAT_PORT), networkInterface);
                                Log.d("wlan多播写", "初始化完成！");
                            }else {
                                Log.d("wlan多播写", "初始化失败！");
                                multicastSocket.close();
                                return;
                            }
                        }else {
                            multicastSocket.setNetworkInterface(networkInterface);
                            //multicastSocket.joinGroup(new InetSocketAddress(multicastAddress, MULTICSAT_PORT), networkInterface);
                            Log.d("p2p多播写", "初始化完成！");
                        }

                    }else {
                        multicastSocket.close();
                        return;
                    }
                }
                // messageType/(macAddress)/total_packets/sequence_number/gson

                if(messageType == GO_MULTICAST_MEMMAP){
                    Log.d(TAG, "发送组播包memMap！！");
                    Gson gson = new Gson();
                    String str;
                    if(writeInterfaceRegrex.equals(WLAN_INTERFACE_REGREX)){
                        Map<String, Member> newMemMap = new HashMap<>();
                        for (Entry<String, Member> entry : memberMap.entrySet()){
                            if(!entry.getKey().equals(macOfP2P)){
                                newMemMap.put(entry.getKey(), entry.getValue());
                            }else {
                                Log.d(TAG, "使用Wlan接口发送时，需要剔除本机设备信息-"+macOfP2P+"/macofP2P-"+macAddress);
                            }
                        }
                        interfaceType = 'w';
                        otherGOMAC = macAddress;
                        //第一次发送消息，不存在中间节点进行转发的过程，因此macAddress与otherGOMAC相同，均为当前组主的MAC。
                        str = messageType + "/" + macAddress + "/" + otherGOMAC + "/" + interfaceType + "/" + gson.toJson(newMemMap);
                    }else {
                        interfaceType = 'p';
                        otherGOMAC = macAddress;
                        str = messageType + "/" + macOfP2P + "/" + otherGOMAC + "/" + interfaceType + "/" +gson.toJson(memberMap);
                    }
                    int gsonLength = str.getBytes().length;
                    if(gsonLength > DATA_LEN){
                        Log.d(TAG, "memberMap长度大于1472，拆分为多个包发送");
                        for(int i = 0; i < gsonLength; i++){
                            if(gsonLength - 1 - i > DATA_LEN){
                                buf = str.substring(i, i + DATA_LEN - 1).getBytes();
                                outPacket = new DatagramPacket(buf, buf.length, multicastAddress, MULTICSAT_PORT);
                                multicastSocket.send(outPacket);
                                i += DATA_LEN - 1;
                            }else {
                                Log.d(TAG, "最后一个包");
                                buf = str.substring(i, gsonLength - 1).getBytes();
                                outPacket = new DatagramPacket(buf, buf.length, multicastAddress, MULTICSAT_PORT);
                                multicastSocket.send(outPacket);
                                break;
                            }

                        }
                    }else {
                        Log.d(TAG, "memberMap长度小于1472，不需要拆分为多个包");
                        buf = str.getBytes();
                        outPacket = new DatagramPacket(buf, buf.length, multicastAddress, MULTICSAT_PORT);
                        multicastSocket.send(outPacket);
                    }
                }else if(messageType == GO_MULTICAST_MEMMAP_FORWARD){
                    Log.d(TAG, "转发组播包memMap！！");
                    Gson gson = new Gson();
                    String str;
                    //存在中间节点转发，因此两个地址不同，macAddress当前设备的MAC；otherGOMAC为其他组组主（MemberMap对应的组）的MAC
                    if(writeInterfaceRegrex.equals(WLAN_INTERFACE_REGREX)){
                        interfaceType = 'w';
                        str = GO_MULTICAST_MEMMAP + "/" + macAddress + "/" + otherGOMAC + "/" + interfaceType + "/" + gson.toJson(memberMap);
                    }else {
                        interfaceType = 'p';
                        str = GO_MULTICAST_MEMMAP + "/" + macAddress + "/" + otherGOMAC + "/" + interfaceType + "/" +gson.toJson(memberMap);
                    }
                    int gsonLength = str.getBytes().length;
                    if (gsonLength > DATA_LEN) {
                        Log.d(TAG, "memberMap长度大于1472，拆分为多个包发送");
                        for (int i = 0; i < gsonLength; i++) {
                            if (gsonLength - 1 - i > DATA_LEN) {
                                buf = str.substring(i, i + DATA_LEN - 1).getBytes();
                                outPacket = new DatagramPacket(buf, buf.length, multicastAddress, MULTICSAT_PORT);
                                multicastSocket.send(outPacket);
                                i += DATA_LEN - 1;
                            } else {
                                Log.d(TAG, "最后一个包");
                                buf = str.substring(i, gsonLength - 1).getBytes();
                                outPacket = new DatagramPacket(buf, buf.length, multicastAddress, MULTICSAT_PORT);
                                multicastSocket.send(outPacket);
                                break;
                            }

                        }
                    } else {
                        Log.d(TAG, "memberMap长度小于1472，不需要拆分为多个包");
                        buf = str.getBytes();
                        outPacket = new DatagramPacket(buf, buf.length, multicastAddress, MULTICSAT_PORT);
                        multicastSocket.send(outPacket);
                    }

                }else if(messageType == GM_MULTICAST_DETECT){
                    //System.out.println("看这里 "+multicastSocket.getInterface().getHostName());
                    buf = (GM_MULTICAST_DETECT +"/1231").getBytes();
                    outPacket = new DatagramPacket(buf, buf.length, multicastAddress, MULTICSAT_PORT);
                    multicastSocket.send(outPacket);
                }else if(messageType == GO_MULTICAST_DELETE){
                    Gson gson = new Gson();
                    String str;
                    //otherGOMAC与macAddress相同
                    str = messageType + "/" + macAddress + "/" + gson.toJson(delMember);

                    buf = str.getBytes();
                    outPacket = new DatagramPacket(buf, str.length(), multicastAddress, MULTICSAT_PORT);
                    multicastSocket.send(outPacket);
                }else if(messageType == GO_MULTICAST_DELETE_FORWARD){
                    Gson gson = new Gson();
                    String str;
                    if(writeInterfaceRegrex.equals(WLAN_INTERFACE_REGREX)){
                        str = GO_MULTICAST_DELETE + "/" + otherGOMAC + "/" + gson.toJson(delMember);
                    }else {
                        str = GO_MULTICAST_DELETE + "/" + otherGOMAC + "/" + gson.toJson(delMember);
                    }
                    buf = str.getBytes();
                    outPacket = new DatagramPacket(buf, str.length(), multicastAddress, MULTICSAT_PORT);
                    multicastSocket.send(outPacket);
                }
                this.close();
            } catch (SocketException e) {
                e.printStackTrace();
                this.close();
            } catch (IOException e) {
                e.printStackTrace();
                this.close();
            }

        }else if(type == MULTICAST_READ){
            try {
                if (multicastSocket == null || multicastSocket.isClosed()) {
                    multicastAddress = InetAddress.getByName(MULTICAST_IP);
                    multicastSocket = new MulticastSocket(MULTICSAT_PORT);
                    multicastSocket.setLoopbackMode(true);
                    Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
                    String regrex = readInterfaceRegrex;
                    if(regrex.equals(P2P_INTERFACE_REGREX)){
                        macOfP2P = AddressObtain.getWlanMACAddress();
                    }else {
                        macOfP2P = AddressObtain.getP2pMACAddress();
                    }
                    Pattern pattern = Pattern.compile(regrex);
                    while (networkInterfaces.hasMoreElements()) {
                        NetworkInterface networkInterface1 = networkInterfaces.nextElement();
                        Matcher matcher = pattern.matcher(networkInterface1.getName());
                        if (matcher.find()) {
                            networkInterface = networkInterface1;
                            macAddress = getMacFromBytes(networkInterface.getHardwareAddress());
                            Log.d("NetworkInterface", "匹配/" + networkInterface.getName() +"/"+ macAddress);
                            break;
                        }
                    }
                    if(networkInterface != null && networkInterface.getInetAddresses() != null){
                        if(readInterfaceRegrex.equals(WLAN_INTERFACE_REGREX)){
                            multicastSocket.joinGroup(new InetSocketAddress(multicastAddress, MULTICSAT_PORT), networkInterface);
                            Log.d("wlan多播读", "初始化完成！");
                        }else {
                            multicastSocket.joinGroup(new InetSocketAddress(multicastAddress, MULTICSAT_PORT), networkInterface);
                            Log.d("p2p多播读", "初始化完成！");
                        }

                        //multicastSocket.setNetworkInterface(networkInterface);

                    }
                }
            } catch (SocketException e) {
                e.printStackTrace();
                this.close();
            } catch (UnknownHostException e) {
                e.printStackTrace();
                this.close();
            } catch (IOException e) {
                e.printStackTrace();
                this.close();
            }
            while (true){
                try {
                    multicastSocket.receive(inPacket);
                    System.out.println("组播收到的信息为："+ new String(buf, 0, inPacket.getLength()));
                    System.out.println("地址："+ inPacket.getAddress().getHostAddress());
                    String str = new String(inPacket.getData(), 0, inPacket.getLength());
                    String mStr[] = str.split("/");
                    if(mStr[0].charAt(0) == GO_MULTICAST_MEMMAP){
                        if(mStr[2].equals(macAddress) || mStr[2].equals(macOfP2P)){
                            Log.d(TAG, "收到本机memberMap，不做处理");
                        }else {
                            Log.d(TAG, "继续组播memberMap");
                            Message msg = new Message();
                            msg.what = 10;
                            Bundle bundle = new Bundle();
                            bundle.putString("macAddressofRelay", mStr[1]);
                            bundle.putString("otherGOMAC", mStr[2]);
                            bundle.putChar("interfaceType", mStr[3].charAt(0));
                            bundle.putString("memberMap", mStr[4]);
                            msg.setData(bundle);
                            mHandler.sendMessage(msg);
                            //使用另一个网卡接口将该消息再组播给另外一个GO multicastWrite
                            //把mac、interface转换成当前设备所对应的参数值，再继续组播
                            Gson multicastMemberMapGson = new Gson();
                            Map<String, Member> multicastMemberMap = multicastMemberMapGson.fromJson(mStr[4].trim(), new TypeToken<Map<String, Member>>(){}.getType());
                            if(mStr[1].equals(mStr[2])){

                                if(mStr[3].charAt(0) == 'p'){
                                    multicastMemberMap.remove(AddressObtain.getWlanMACAddress());
                                    Log.d(TAG, "第一个中继GO，删除本机mac/p " + AddressObtain.getWlanMACAddress());
                                }else {
                                    multicastMemberMap.remove(AddressObtain.getP2pMACAddress());
                                    Log.d(TAG, "第一个中继GO，删除本机mac/w " + AddressObtain.getP2pMACAddress());
                                }

                            }

                            //需要修改。。。。。同时使用两个接口组播
                            if(readInterfaceRegrex.equals(P2P_INTERFACE_REGREX)){
                                MulticastThread multicastThread = new MulticastThread(wiFiDirectActivity, MULTICAST_WRITE, GO_MULTICAST_MEMMAP_FORWARD, mStr[2], WLAN_INTERFACE_REGREX, multicastMemberMap);
                                new Thread(multicastThread).start();
                            }else {
                                MulticastThread multicastThread = new MulticastThread(wiFiDirectActivity, MULTICAST_WRITE, GO_MULTICAST_MEMMAP_FORWARD, mStr[2], P2P_INTERFACE_REGREX, multicastMemberMap);
                                new Thread(multicastThread).start();
                            }
                        }
                    }else if(mStr[0].charAt(0) == GO_MULTICAST_MEMMAP_FORWARD){

                    }else if(mStr[0].charAt(0) == GM_MULTICAST_DETECT){
                        Message msg = new Message();
                        msg.what = 4;
                        Bundle bundle = new Bundle();
                        bundle.putString("data", new String(buf, 0, inPacket.getLength()));
                        bundle.putString("address", inPacket.getAddress().getHostAddress());
                        msg.setData(bundle);
                        mHandler.sendMessage(msg);
                    } else if(mStr[0].charAt(0) == GO_MULTICAST_DELETE){
                        Message msg = new Message();
                        msg.what = 9;
                        Bundle bundle = new Bundle();
                        bundle.putString("macofGO", mStr[1]);
                        bundle.putString("member", mStr[2]);
                        msg.setData(bundle);
                        mHandler.sendMessage(msg);
                        //使用另一个网卡接口将该消息再组播给另外一个GO multicastWrite
                        Gson gson = new Gson();
                        Member  member = gson.fromJson(mStr[2].trim(), new TypeToken<Member>(){}.getType());


                        //需要修改。。。。。同时使用两个接口组播

                        if(readInterfaceRegrex.equals(P2P_INTERFACE_REGREX)){
                            MulticastThread multicastThread = new MulticastThread(wiFiDirectActivity, MULTICAST_WRITE, GO_MULTICAST_DELETE_FORWARD, WLAN_INTERFACE_REGREX, member, mStr[1]);
                            new Thread(multicastThread).start();
                        }else {
                            MulticastThread multicastThread = new MulticastThread(wiFiDirectActivity, MULTICAST_WRITE, GO_MULTICAST_DELETE_FORWARD, P2P_INTERFACE_REGREX, member, mStr[1]);
                            new Thread(multicastThread).start();
                        }
                        //广播给组员
                        Log.d(TAG, "继续组播delMember-广播给组员");
                        UDPBroadcast udpBroadcast = new UDPBroadcast(UDPBroadcast.BROADCAST_WRITE, UDPBroadcast.DELETE_MEMBER, member);
                        new Thread(udpBroadcast).start();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    this.close();
                    break;
                }
            }
        }
    }
    public void close(){
        if(multicastSocket != null && !multicastSocket.isClosed()) {

                //multicastSocket.leaveGroup(InetAddress.getByName(MULTICAST_IP));
                multicastSocket.close();
                multicastSocket = null;
            System.out.println("MulticastSocket关闭！！！！！！");
        }
    }

    public String getMacFromBytes(byte[] macBytes) {
        String mac = "";
        for(int i = 0;i < macBytes.length; i++){
            String sTemp = Integer.toHexString(0xFF &  macBytes[i]);
            if(sTemp.length() == 1){
                mac += "0" + sTemp + ":";
            }else {
                mac += sTemp + ":";
            }

        }

        mac = mac.substring(0,mac.lastIndexOf(":"));
        return mac;
    }

}
