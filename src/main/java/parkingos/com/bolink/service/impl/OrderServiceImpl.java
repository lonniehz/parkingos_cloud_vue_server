package parkingos.com.bolink.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import parkingos.com.bolink.dao.mybatis.OrderTbExample;
import parkingos.com.bolink.dao.mybatis.mapper.BolinkDataMapper;
import parkingos.com.bolink.dao.mybatis.mapper.OrderMapper;
import parkingos.com.bolink.dao.spring.CommonDao;
import parkingos.com.bolink.models.CarpicTb;
import parkingos.com.bolink.models.ComPassTb;
import parkingos.com.bolink.models.OrderTb;
import parkingos.com.bolink.models.UserInfoTb;
import parkingos.com.bolink.orderserver.OrderServer;
import parkingos.com.bolink.service.CommonService;
import parkingos.com.bolink.service.OrderService;
import parkingos.com.bolink.service.SupperSearchService;
import parkingos.com.bolink.utils.*;

import javax.servlet.http.HttpServletResponse;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service("orderSpring")
public class OrderServiceImpl implements OrderService {

    Logger logger = LoggerFactory.getLogger(OrderServiceImpl.class);

    @Autowired
    private CommonDao commonDao;
    @Autowired
    private SupperSearchService<OrderTb> supperSearchService;
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderServer orderServer;
    @Autowired
    private BolinkDataMapper bolinkDataMapper;
    @Autowired
    private CommonService commonService;

    @Override
    public int selectCountByConditions(OrderTb orderTb) {
        return 0;
    }

    @Override
    public JSONObject selectResultByConditions(Map<String, String> reqmap) {


        JSONObject result = new JSONObject();

        //查询三天的数据显示
        logger.error("=========..req" + reqmap.size());
        Long comid = Long.parseLong(reqmap.get("comid"));
        //根据comid获取 cityid  命中分表 约束
        Long groupid = orderMapper.getGroupIdByComId(comid);
        Long cityid = -1L;
        if (groupid != null && groupid > 0) {
            cityid = orderMapper.getCityIdByGroupId(groupid);
        } else {
            cityid = orderMapper.getCityIdByComId(comid);
        }
        logger.info("select city by comid:" + cityid);
        if (cityid != null && cityid > -1) {
            reqmap.put("cityId",cityid+"");
            reqmap.put("tableName","order_tb_new_"+cityid%100);
        }else{
            reqmap.put("tableName","order_tb_new");
        }

        String createTime = reqmap.get("create_time");
        String endTime = reqmap.get("end_time");
        logger.error("===>>>createTime" + createTime + "~~~~endTime:" + endTime);
        //组装 一个月 参数
        if (endTime == null || "".equals(endTime)) {
            if (createTime == null || "undefined".equals(createTime) || "".equals(createTime)) {
                reqmap.put("create_time", "1");
                reqmap.put("create_time_start", (TimeTools.getToDayBeginTime() - 2 * 86400) + "");
                logger.error("=========..req" + reqmap.size());
            }
        }
        String rp = "20";
        if(reqmap.get("rp")!=null){
            rp = reqmap.get("rp");
        }

//        int count = getOrdersCountByComid(reqmap);
        int count = orderServer.selectOrdersCount(reqmap);
        logger.info("get orderCount by service :"+count);
        List<OrderTb> orderList =new ArrayList<OrderTb>();
        List<Map<String, Object>> resList = new ArrayList<Map<String, Object>>();
        if(count>0) {
            if(reqmap.get("export")==null){//不是导出
                reqmap.put("rp",rp);
            }
//            orderList = getOrderListByComid(reqmap);
            orderList = orderServer.getOrdersByMapConditons(reqmap);
            if (orderList != null && orderList.size() > 0) {
                for (OrderTb order : orderList) {
//                    logger.info("get order ======:"+order);
                    OrmUtil<OrderTb> om = new OrmUtil<OrderTb>();
                    Map map = om.pojoToMap(order);
//                    logger.info("get order map======:"+map);
                    Long start = (Long) map.get("create_time");
                    Long end = (Long) map.get("end_time");
                    if (start != null && end != null) {
                        map.put("duration", StringUtils.getTimeString(start, end));
                    } else {
                        map.put("duration", "");
                    }

//                    String carNumber = map.get("car_number")+"";
//                    String orderId = map.get("order_id_local")+"";
//                    JSONObject moneyData = getOrderDetail(orderId,comid,carNumber);
//                    map.put("electronic_prepay",moneyData.get("ele_prepay"));
//                    map.put("electronic_pay",moneyData.get("ele_pay"));
//                    map.put("cash_prepay",moneyData.get("cash_prepay"));
                    resList.add(map);
                }
            }
        }
        result.remove("rows");
        result.put("rows", JSON.toJSON(resList));
        result.put("total",count);
        return result;
    }

    private int getOrdersCountByComid(Map<String, String> reqmap) {
        logger.info("~~~~~reamap 1"+reqmap);
        OrderTbExample example = new OrderTbExample();
        example.createCriteria().andIshdEqualTo(0);
        example.createCriteria().andComidEqualTo(Long.valueOf(reqmap.get("comid")));
        reqmap.remove("rp");
        example = ExampleUtis.createOrderExample(example,reqmap);
        int count = 0;
        try{
            count = orderMapper.selectOrdersCount(example);
        }catch(Exception e){
            logger.error("查询失败。。。",e);
        }
        logger.info("count~~~~~~~~"+count);
        return count;
    }

    private List<OrderTb> getOrderListByComid(Map<String, String> reqmap) {
        logger.info("~~~~~reamap 2"+reqmap);
        OrderTbExample example = new OrderTbExample();
        example.createCriteria().andIshdEqualTo(0);
        example.createCriteria().andComidEqualTo(Long.valueOf(reqmap.get("comid")));
        example = ExampleUtis.createOrderExample(example,reqmap);
        //logger.info("example~~~~~~~~"+example);
        List<OrderTb> orders = orderMapper.selectOrders(example);
        return orders;
    }

    @Override
    public JSONObject getPicResult(String orderid, Long comid) {

        String str = "{\"in\":[],\"out\":[]}";
        JSONObject result = JSONObject.parseObject(str);

//        Long groupId = orderMapper.getGroupIdByComId(comid);
//        Long cityid = -1L;
//        if(groupId!=null&&groupId>-1){
//            cityid = orderMapper.getCityIdByGroupId(groupId);
//        }else {
//            cityid = orderMapper.getCityIdByComId(comid);
//        }

        DB db = MongoClientFactory.getInstance().getMongoDBBuilder("zld");
        String collectionName = "";
//        String tableName = "";
//        if(cityid!=null&&cityid>-1){
//            tableName = "order_tb_new_"+cityid%100;
//        }else{
//            tableName="order_tb_new";
//        }
//        List<OrderTb> list = orderServer.qryOrdersByComidAndOrderId(comid,orderid,tableName);
//        if (list != null && list.size() > 0) {
//            if (list.get(0).getCarpicTableName() != null) {
//                collectionName = list.get(0).getCarpicTableName();
//            }
//        }

//        DBCollection collection = db.getCollection("collectionName");
//        DBCollection collection = db.getCollection(collectionName);

        List<String> inlist = new ArrayList<>();
        List<String> outlist = new ArrayList<>();
        inlist.add("/order/carpicsup?comid=" + comid + "&typeNew=in&orderid=" + orderid+"&timestemp="+System.currentTimeMillis());
        outlist.add("/order/carpicsup?comid=" + comid + "&typeNew=out&orderid=" + orderid+"&timestemp="+System.currentTimeMillis());
//        logger.error("=======>>>获取订单图片..inlist..outlist" + inlist.size() + "==>>" + outlist.size());
//        if (collection != null) {
//            BasicDBObject document = new BasicDBObject();
//            document.put("parkid", String.valueOf(comid));
//            document.put("orderid", orderid + "");
//            document.put("gate", "in");
//            Long insize = collection.count(document);
//            document.put("gate", "out");
//            Long outsize = collection.count(document);
//
//            if (insize > 1) {
//                for (int i = 0; i < insize; i++) {
//                    inlist.add("/order/carpicsup?comid=" + comid + "&typeNew=in&currentnum=" + i + "&orderid=" + orderid);
//                }
//            }
//            if (outsize > 1) {
//                for (int i = 0; i < outsize; i++) {
//                    outlist.add("/order/carpicsup?comid=" + comid + "&typeNew=out&currentnum=" + i + "&orderid=" + orderid);
//                }
//            }
//        }

        result.put("in", JSON.toJSON(inlist));
        result.put("out", JSON.toJSON(outlist));
        return result;
    }

    @Override
    public String getCarPics(String orderid, Long comid, String type, HttpServletResponse response) throws Exception{
        logger.error("getcarPic from mongodb file:orderid=" + orderid + "type=" + type + ",comid:" + comid);
        if (orderid != null && type != null) {

            //根据订单编号查询出mongodb中存入的对应个表名
            //Map map = daService.getMap("select * from carpic_tb where order_id=? and comid=?", new Object[]{orderidlocal,String.valueOf(comid)});
            CarpicTb carpicTb = new CarpicTb();
            carpicTb.setOrderId(orderid + "");
            carpicTb.setComid(comid + "");
            carpicTb = (CarpicTb) commonDao.selectObjectByConditions(carpicTb);

            String collectionName = "";
            String picUrl = "";
            if (carpicTb != null) {
                if("in".equals(type)){
                    if(!Check.isEmpty(carpicTb.getInOrderPic())){
                        picUrl = carpicTb.getInOrderPic();
                        response.sendRedirect(picUrl);
                        return null;
                    }
                }else{
                    if(!Check.isEmpty(carpicTb.getOutOrderPic())){
                        picUrl = carpicTb.getOutOrderPic();
                        response.sendRedirect(picUrl);
                        return null;
                    }
                }
                collectionName = carpicTb.getCarpicTableName();
            }
            DB db = MongoClientFactory.getInstance().getMongoDBBuilder("zld");
            if (collectionName == null || "".equals(collectionName) || "null".equals(collectionName)) {
                logger.error(">>>>>>>>>>>>>查询图片错误........");
                response.sendRedirect("http://"+CustomDefind.getValue("DOMAIN")+"/default.png");
                return null;
            }

            DBCollection collection = db.getCollection(collectionName);
            if (collection != null) {
                BasicDBObject document = new BasicDBObject();
                document.put("parkid", String.valueOf(comid));
                document.put("orderid", orderid + "");
                document.put("gate", type);
                DBObject obj = collection.findOne(document);
                if (obj == null) {
                    response.sendRedirect("http://"+CustomDefind.getValue("DOMAIN")+"/default.png");
                    return null;
                }
                byte[] content = (byte[]) obj.get("content");
                db.requestDone();
                logger.error("取图片成功.....大小:" + content.length);
                response.setDateHeader("Expires", System.currentTimeMillis()+12*60*60*1000);
                response.setContentLength(content.length);
                response.setContentType("image/jpeg");
                OutputStream o = response.getOutputStream();
                o.write(content);
                o.flush();
                o.close();
            } else {
                response.sendRedirect("http://"+CustomDefind.getValue("DOMAIN")+"/default.png");
                return null;
            }
        } else {
            response.sendRedirect("http://"+CustomDefind.getValue("DOMAIN")+"/default.png");
            return null;
        }
        return null;
    }

    @Override
    public List<List<Object>> exportExcel(Map<String, String> reqParameterMap) {

        //删除分页条件  查询该条件下所有  不然为一页数据
        reqParameterMap.remove("rp");
        //标记为导出
        reqParameterMap.put("export","1");

        //获得要导出的结果
        JSONObject result = selectResultByConditions(reqParameterMap);

        Long comid = Long.parseLong(reqParameterMap.get("comid"));

        List<OrderTb> orderlist = JSON.parseArray(result.get("rows").toString(), OrderTb.class);

        logger.error("=========>>>>>>.导出订单" + orderlist.size());
        List<List<Object>> bodyList = new ArrayList<List<Object>>();
//        List<List<String>> bodyList = new ArrayList<List<String>>();
        if (orderlist != null && orderlist.size() > 0) {
            String[] f = new String[]{"c_type", "car_number", "car_type", "create_time", "end_time", "duration", "pay_type", "freereasons", "amount_receivable", "total", "electronic_prepay", "cash_prepay", "electronic_pay", "cash_pay", "reduce_amount", "uid", "out_uid", "state", "in_passid", "out_passid", "order_id_local"};
            Map<Long, String> uinNameMap = new HashMap<Long, String>();
            Map<Integer, String> passNameMap = new HashMap<Integer, String>();
            for (OrderTb orderTb : orderlist) {
//                List<String> values = new ArrayList<String>();
                List<Object> values = new ArrayList<Object>();
                OrmUtil<OrderTb> otm = new OrmUtil<>();
                Map map = otm.pojoToMap(orderTb);
                for (String field : f) {
                    Object v = map.get(field);
                    if (v == null)
                        v = "";
                    if ("uid".equals(field) || "out_uid".equals(field)) {
                        Long uid = -1L;
                        if (Check.isLong(v + ""))
                            uid = Long.valueOf(v + "");
                        if (uinNameMap.containsKey(uid))
                            values.add(uinNameMap.get(uid));
                        else {
                            String name = getUinName(Long.valueOf(map.get(field) + ""));
                            values.add(name);
                            uinNameMap.put(uid, name);
                        }
                    } else if ("c_type".equals(field)) {
                        if (Check.isLong(v + "")) {
                            switch (Integer.valueOf(v + "")) {//0:NFC,1:IBeacon,2:照牌   3通道照牌 4直付 5月卡用户
                                case 0:
                                    values.add("NFC刷卡");
                                    break;
                                case 1:
                                    values.add("Ibeacon");
                                    break;
                                case 2:
                                    values.add("手机扫牌");
                                    break;
                                case 3:
                                    values.add("通道扫牌");
                                    break;
                                case 4:
                                    values.add("直付");
                                    break;
                                case 5:
                                    values.add("月卡");
                                    break;
                                default:
                                    values.add("");
                            }
                        } else {
                            values.add(v + "");
                        }
                    } else if ("duration".equals(field)) {
                        Long start = (Long) map.get("create_time");
                        Long end = (Long) map.get("end_time");
                        if (start != null && end != null) {
                            values.add(StringUtils.getTimeString(start, end));
                        } else {
                            values.add("");
                        }
                    } else if ("pay_type".equals(field)) {
                        switch (Integer.valueOf(v + "")) {//0:NFC,1:IBeacon,2:照牌   3通道照牌 4直付 5月卡用户
                            case 1:
                                values.add("现金支付");
                                break;
                            case 2:
                                values.add("手机支付");
                                break;
                            case 3:
                                values.add("包月");
                                break;
                            case 8:
                                values.add("免费");
                                break;
                            default:
                                values.add("");
                        }
                    } else if ("state".equals(field)) {
                        switch (Integer.valueOf(v + "")) {//0:NFC,1:IBeacon,2:照牌   3通道照牌 4直付 5月卡用户
                            case 0:
                                values.add("未结算 ");
                                break;
                            case 1:
                                values.add("已结算 ");
                                break;
                            default:
                                values.add("");
                        }
                    } else if ("isclick".equals(field)) {
                        switch (Integer.valueOf(map.get(field) + "")) {//0:NFC,1:IBeacon,2:照牌   3通道照牌 4直付 5月卡用户
                            case 0:
                                values.add("系统结算");
                                break;
                            case 1:
                                values.add("手动结算");
                                break;
                            default:
                                values.add("");
                        }
                    } else if ("in_passid".equals(field) || "out_passid".equals(field)) {
                        if (!"".equals(v.toString()) && Check.isNumber(v.toString())) {
                            Integer passId = Integer.valueOf(v.toString());
                            if (passNameMap.containsKey(passId))
                                values.add(passNameMap.get(passId));
                            else {
                                String passName = getPassName(comid, passId);
                                values.add(passName);
                                passNameMap.put(passId, passName);
                            }
                        } else {
                            values.add(v + "");
                        }
                    } else {
                        if ("create_time".equals(field) || "end_time".equals(field)) {
                            if (!"".equals(v.toString())) {
                                values.add(TimeTools.getTime_yyyyMMdd_HHmmss(Long.valueOf((v + "")) * 1000));
                            } else {
                                values.add("null");
                            }
                        } else {
                            values.add(v + "");
                        }
                    }
                }
                bodyList.add(values);
            }
        }
        return bodyList;
    }

    @Override
    public Long getComidByOrder(Long id) {
        OrderTb orderTb = new OrderTb();
        orderTb.setId(id);
        orderTb = (OrderTb) commonDao.selectObjectByConditions(orderTb);
        if (orderTb != null && orderTb.getComid() != null) {
            return orderTb.getComid();
        }
        return -1L;
    }

    @Override
    public void resetDataByComid(Long comid) {
        Long groupId = orderMapper.getGroupIdByComId(comid);
        Long cityid = -1L;
        if(groupId!=null&&groupId>-1){
            cityid = orderMapper.getCityIdByGroupId(groupId);
        }else {
            cityid = orderMapper.getCityIdByComId(comid);
        }
        String tableName = "order_tb_new";
        if(cityid!=null&&cityid>0){
            tableName= tableName+"_"+cityid%100;
        }
        orderServer.resetDataByComid(comid,tableName,cityid);
    }

    @Override
    public JSONObject getOrderDetail(String orderid, Long comid, String carNumber) {

        String tableName = commonService.getTableNameByComid(comid,1);
        List<Map<String,Object>> list = bolinkDataMapper.getOrderMoneys(tableName,orderid,comid,carNumber);
        JSONObject result = new JSONObject();
        result.put("cash_prepay",0.0);
        result.put("ele_prepay",0.0);
        result.put("ele_pay",0.0);
        // 1现金预付 2电子预付 3电子支付
        if(list!=null&&list.size()>0){
            for(Map moneyMap:list){
                if((int)moneyMap.get("type")==1){
                    result.put("cash_prepay",moneyMap.get("pay_money"));
                }else if((int)moneyMap.get("type")==2){
                    result.put("ele_prepay",moneyMap.get("pay_money"));
                }else if((int)moneyMap.get("type")==3){
                    result.put("ele_pay",moneyMap.get("pay_money"));
                }
            }
        }
        return result;
    }

    private String getPassName(Long comId, Integer passId) {
        ComPassTb comPassTb = new ComPassTb();
        comPassTb.setComid(comId);
        comPassTb.setId(passId.longValue());
        comPassTb = (ComPassTb) commonDao.selectObjectByConditions(comPassTb);
        if (comPassTb != null && comPassTb.getPassname() != null) {
            return comPassTb.getPassname();
        }
        return "";
    }

    private String getUinName(Long uin) {
        UserInfoTb userInfoTb = new UserInfoTb();
        userInfoTb.setId(uin);
        userInfoTb = (UserInfoTb) commonDao.selectObjectByConditions(userInfoTb);

        String uinName = "";
        if (userInfoTb != null && userInfoTb.getNickname() != null) {
            return userInfoTb.getNickname();
        }
        return uinName;
    }
}
