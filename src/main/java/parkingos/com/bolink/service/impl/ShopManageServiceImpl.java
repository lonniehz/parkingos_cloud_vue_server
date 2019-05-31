package parkingos.com.bolink.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import parkingos.com.bolink.dao.spring.CommonDao;
import parkingos.com.bolink.enums.FieldOperator;
import parkingos.com.bolink.models.ComInfoTb;
import parkingos.com.bolink.models.ParkLogTb;
import parkingos.com.bolink.models.ShopAccountTb;
import parkingos.com.bolink.models.ShopTb;
import parkingos.com.bolink.qo.PageOrderConfig;
import parkingos.com.bolink.qo.SearchBean;
import parkingos.com.bolink.service.SaveLogService;
import parkingos.com.bolink.service.ShopManageService;
import parkingos.com.bolink.service.SupperSearchService;
import parkingos.com.bolink.utils.Check;
import parkingos.com.bolink.utils.OrmUtil;
import parkingos.com.bolink.utils.RequestUtil;
import parkingos.com.bolink.utils.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ShopManageServiceImpl implements ShopManageService {

    Logger logger = LoggerFactory.getLogger( ShopManageServiceImpl.class );

    @Autowired
    private CommonDao commonDao;
    @Autowired
    private SaveLogService saveLogService;
    @Autowired
    private SupperSearchService<ShopTb> supperSearchService;

    @Override
    public String addMoney(HttpServletRequest request) {

        Long comid = RequestUtil.getLong(request,"comid",-1L);
        String nickname = StringUtils.decodeUTF8(RequestUtil.getString(request,"nickname1"));
        Long uin = RequestUtil.getLong(request, "loginuin", -1L);

        Long shoppingmarket_id = RequestUtil.getLong( request, "shop_id", -1L );

        if (shoppingmarket_id == -1) {
            return "{\"state\":0}";
        }
        ShopTb queryShopTb = new ShopTb();
        queryShopTb.setId( shoppingmarket_id );

        ShopTb shopTb = (ShopTb) commonDao.selectObjectByConditions( queryShopTb );

        Integer ticket_time = RequestUtil.getInteger( request, "ticket_time", 0 );
        Integer ticket_money = RequestUtil.getInteger( request, "ticket_money", 0 );
        double addmoney = RequestUtil.getDouble( request, "addmoney", 0.00 );
        //减免类型
        Integer ticket_type = Integer.parseInt( shopTb.getTicketType() + "" );
        if(addmoney<0){
            //有可能全免券和优惠券都为0
            return "{\"state\":0}";
        }
        if (ticket_type == 1) {
            if (0 > ticket_time) {
                //StringUtils.ajaxOutput( resp, "减免小时必须输入正整数" );
                return "{\"state\":0}";
            }
        } else {
            if (0 > ticket_money) {
                //StringUtils.ajaxOutput( resp, "减免券必须输入正整数" );
                return "{\"state\":0}";
            }
        }
        Integer ticketfree_limit = RequestUtil.getInteger( request, "ticketfree_limit", 0 );
        if(ticketfree_limit<0){
            return "{\"state\":0}";
        }
        shopTb.setTicketLimit( shopTb.getTicketLimit() + ticket_time );
        shopTb.setTicketfreeLimit( shopTb.getTicketfreeLimit() + ticketfree_limit );
        shopTb.setTicketMoney( shopTb.getTicketMoney() + ticket_money );
        commonDao.updateByPrimaryKey( shopTb );

        ShopAccountTb shopAccountTb = new ShopAccountTb();
        shopAccountTb.setShopId( Integer.parseInt( shopTb.getId() + "" ) );
        shopAccountTb.setShopName( shopTb.getName() );
        shopAccountTb.setTicketLimit( ticket_time );
        shopAccountTb.setTicketfreeLimit( ticketfree_limit );
        shopAccountTb.setTicketMoney( ticket_money );
        shopAccountTb.setAddMoney( new BigDecimal( addmoney ) );
        shopAccountTb.setOperateTime( System.currentTimeMillis() / 1000 );
        shopAccountTb.setOperateType( 1 );
        shopAccountTb.setParkId( RequestUtil.getLong( request, "parkid", -1L ) );
        shopAccountTb.setStrid( "test" );
        shopAccountTb.setOperator( RequestUtil.getLong( request, "operator", -1L ) );
        int insert = commonDao.insert( shopAccountTb );

        if(insert==1){
            ParkLogTb parkLogTb = new ParkLogTb();
            parkLogTb.setOperateUser(nickname);
            parkLogTb.setOperateTime(System.currentTimeMillis()/1000);
            parkLogTb.setOperateType(2);
            parkLogTb.setContent(uin+"("+nickname+")给商户"+shoppingmarket_id+"续费"+addmoney+"元");
            parkLogTb.setType("shop");
            parkLogTb.setParkId(comid);
            saveLogService.saveLog(parkLogTb);
        }

        return "{\"state\":" + insert + "}";
    }

    @Override
    public int updateComSuperimposed(ComInfoTb comInfoTb) {
        return commonDao.updateByPrimaryKey(comInfoTb);
    }

    @Override
    public String refund(HttpServletRequest request) {

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("state",0);
        Long comid = RequestUtil.getLong(request,"comid",-1L);
        String nickname = StringUtils.decodeUTF8(RequestUtil.getString(request,"nickname1"));
        Long uin = RequestUtil.getLong(request, "loginuin", -1L);

        Long shopId = RequestUtil.getLong( request, "shop_id", -1L );

        ShopTb queryShopTb = new ShopTb();
        queryShopTb.setId( shopId );

        ShopTb shopTb = (ShopTb) commonDao.selectObjectByConditions( queryShopTb );

        Integer ticket_time = RequestUtil.getInteger( request, "ticket_time", 0 );
        Integer ticket_money = RequestUtil.getInteger( request, "ticket_money", 0 );
        double addmoney = RequestUtil.getDouble( request, "addmoney", 0.00 );
        Integer ticketfree_limit = RequestUtil.getInteger( request, "ticketfree_limit", 0 );

        if(ticket_money>shopTb.getTicketMoney()||ticket_time>shopTb.getTicketLimit()||ticketfree_limit>shopTb.getTicketfreeLimit()){
            jsonObject.put("msg","商户余额不足！");
            return jsonObject.toJSONString();
        }

        shopTb.setTicketLimit( shopTb.getTicketLimit() - ticket_time );
        shopTb.setTicketfreeLimit( shopTb.getTicketfreeLimit() - ticketfree_limit );
        shopTb.setTicketMoney( shopTb.getTicketMoney() - ticket_money );
        commonDao.updateByPrimaryKey( shopTb );

        ShopAccountTb shopAccountTb = new ShopAccountTb();
        shopAccountTb.setShopId( Integer.parseInt( shopTb.getId() + "" ) );
        shopAccountTb.setShopName( shopTb.getName() );
        shopAccountTb.setTicketLimit( ticket_time );
        shopAccountTb.setTicketfreeLimit( ticketfree_limit );
        shopAccountTb.setTicketMoney( ticket_money );
        shopAccountTb.setAddMoney( new BigDecimal( addmoney ) );
        shopAccountTb.setOperateTime( System.currentTimeMillis() / 1000 );
        shopAccountTb.setOperateType( 3 );
        shopAccountTb.setParkId( comid );
        shopAccountTb.setOperator( uin );
        int insert = commonDao.insert( shopAccountTb );

        if(insert==1){
            ParkLogTb parkLogTb = new ParkLogTb();
            parkLogTb.setOperateUser(nickname);
            parkLogTb.setOperateTime(System.currentTimeMillis()/1000);
            parkLogTb.setOperateType(2);
            parkLogTb.setContent(uin+"("+nickname+")给商户"+shopId+"退款"+addmoney+"元");
            parkLogTb.setType("shop");
            parkLogTb.setParkId(comid);
            saveLogService.saveLog(parkLogTb);
            jsonObject.put("state",1);
            jsonObject.put("msg","退款成功！");
        }

        return jsonObject.toJSONString();
    }

    @Override
    public String delete(HttpServletRequest request) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("state",0);

        Long comid = RequestUtil.getLong(request,"comid",-1L);
        String nickname = StringUtils.decodeUTF8(RequestUtil.getString(request,"nickname1"));
        Long uin = RequestUtil.getLong(request, "loginuin", -1L);

        //0删除  1 销户
        Integer logoutState = RequestUtil.getInteger(request,"logoutState",0);
        Long id = RequestUtil.getLong( request, "id", -1L );
        int delete = 0;
        if (id > 0) {
            ShopTb shopTb = new ShopTb();
            shopTb.setId(id);
            if(logoutState==0) {
                shopTb.setState(1);
                //删除操作将state状态修改为1
                delete = commonDao.updateByPrimaryKey(shopTb);
            }else if(logoutState==1){
                //查询该商户还有没有余额 ，如果有余额，那么先让他进行退款操作
                shopTb = (ShopTb)commonDao.selectObjectByConditions(shopTb);
                if (shopTb!=null){
                    int ticketLimit =shopTb.getTicketLimit();
                    int ticketfree_limit = shopTb.getTicketfreeLimit();
                    int ticket_money = shopTb.getTicketMoney();
                    if(ticketLimit>0||ticket_money>0||ticketfree_limit>0){
                        jsonObject.put("msg","请退款后再进行销户操作！");
                        return jsonObject.toJSONString();
                    }
                    //销户
                    shopTb.setState(2);
                    delete = commonDao.updateByPrimaryKey(shopTb);
                }
            }
        }

        if(delete==1){
            jsonObject.put("state",1);
            ParkLogTb parkLogTb = new ParkLogTb();
            parkLogTb.setOperateUser(nickname);
            parkLogTb.setOperateTime(System.currentTimeMillis()/1000);
            parkLogTb.setOperateType(3);
            if(logoutState==0) {
                parkLogTb.setContent(uin + "(" + nickname + ")" + "删除了商户" + id);
                jsonObject.put("msg","删除成功！");
            }else if(logoutState==1){
                parkLogTb.setContent(uin + "(" + nickname + ")" + "进行了商户销户操作:" + id);
                jsonObject.put("msg","销户成功！");
            }
            parkLogTb.setType("shop");
            parkLogTb.setParkId(comid);
            saveLogService.saveLog(parkLogTb);
        }
        return jsonObject.toJSONString();
    }

    @Override
    public String quickquery(HttpServletRequest req) {
        Integer pageNum = RequestUtil.getInteger( req, "page", 1 );
        Integer pageSize = RequestUtil.getInteger( req, "rp", 20 );
        String str = "{\"total\":12,\"page\":1,\"rows\":[]}";
        JSONObject result = JSONObject.parseObject( str );

        ShopTb shopTb = new ShopTb();
        shopTb.setComid( Long.valueOf( RequestUtil.processParams( req, "comid" ) ) );
        //state状态 0为正常使用 1为删除状态  2为销户状态(也要显示在列表)
//        shopTb.setState( 0 );
        List<Integer> stateList = new ArrayList<>();
        stateList.add(0);
        stateList.add(2);
        SearchBean searchBean = new SearchBean();
        searchBean.setOperator(FieldOperator.CONTAINS);
        searchBean.setFieldName("state");
        searchBean.setBasicValue(stateList);
        List<SearchBean> searchBeans = new ArrayList<>();
        searchBeans.add(searchBean);
        int count = commonDao.selectCountByConditions(shopTb,searchBeans);
        result.put( "total", count );
        if (count > 0) {
            /**分页处理*/
            PageOrderConfig config = new PageOrderConfig();
            config.setPageInfo( pageNum, pageSize );
            List<ShopTb> list = commonDao.selectListByConditions( shopTb,searchBeans, config );
            List<Map<String, Object>> resList = new ArrayList<>();
            if (list != null && !list.isEmpty()) {
                for (ShopTb sb : list) {
                    OrmUtil<ShopTb> otm = new OrmUtil<>();
                    Map<String, Object> map = otm.pojoToMap( sb );
                    resList.add( map );
                }
                result.put( "rows", JSON.toJSON( resList ) );
            }
            result.put( "total", count );
            result.put( "page", pageNum );
        }
        return result.toJSONString();
    }

    @Override
    public String create(HttpServletRequest request) {

        Long comid = RequestUtil.getLong(request,"comid",-1L);
        String nickname = StringUtils.decodeUTF8(RequestUtil.getString(request,"nickname1"));
        Long uin = RequestUtil.getLong(request, "loginuin", -1L);

        //接收参数
        Long id = RequestUtil.getLong( request, "id", -1L );
        String name = RequestUtil.processParams( request, "name" );
        String address = RequestUtil.processParams( request, "address" );
        String mobile = RequestUtil.processParams( request, "mobile" );
        //String phone = RequestUtil.processParams( request, "phone" );
        Integer ticket_type = RequestUtil.getInteger( request, "ticket_type", 1 );
        Integer handInputEnable = RequestUtil.getInteger( request, "hand_input_enable", 0 );
        Integer supportType = RequestUtil.getInteger(request,"support_type",1);
        Integer freeLimitTimes = RequestUtil.getInteger(request,"free_limit_times",0);
        Integer useFixCode = RequestUtil.getInteger(request,"use_fix_code",1);//默认支持
        //有效期小时  1   有效期分钟2
        Integer limitDayUnit = RequestUtil.getInteger(request,"limit_day_unit",1);


        if(comid==-1){
            return "{\"state\":0,\"msg\":\"车场不存在\"}";
        }
        ComInfoTb con = new ComInfoTb();
        con.setId(comid);
        ComInfoTb comInfoTb = (ComInfoTb) commonDao.selectObjectByConditions(con);
        Integer sup = comInfoTb.getSuperimposed();

        Integer useLimit = RequestUtil.getInteger(request,"use_limit",0);
        //和车场比较这个用券限制，不能超过车场的设置
        if(sup!=1){
            if(sup==0){
                if(useLimit>1){
                    return "{\"state\":0,\"msg\":\"叠加限制不能超过车场\"}";
                }
            }else{
                if(useLimit>0){
                    if(useLimit>sup){
                        return "{\"state\":0,\"msg\":\"叠加限制不能超过车场\"}";
                    }
                }
            }
        }


        String default_limit = RequestUtil.getString( request, "default_limit" );
//        System.out.println("====默认显示额度:"+default_limit);
        if(default_limit.endsWith(",")){
            return "{\"state\":0,\"msg\":\"请输入正确的默认额度\"}";
        }

        if(ticket_type!=3) {
            String[] defaultArr = default_limit.split(",");
            if(defaultArr.length<1){
                return "{\"state\":0,\"msg\":\"请输入正确的默认额度\"}";
            }
            if(defaultArr.length>11){
                return "{\"state\":0,\"msg\":\"最多支持十一个默认额度\"}";
            }
            for (String str : defaultArr) {
                if (!Check.isNumber(str)) {
                    return "{\"state\":0,\"msg\":\"请输入正确的默认额度\"}";
                }
            }
        }else{
            if (default_limit.length()>3||!Check.isDouble(default_limit)) {
                return "{\"state\":0,\"msg\":\"请输入正确的默认额度(0.1-9.9)\"}";
            }
            Double dis = Double.parseDouble(default_limit);
            if(dis<=0||dis>=10){
                return "{\"state\":0,\"msg\":\"请输入正确的默认额度(0.1-9.9)\"}";
            }
        }

        double discount_percent = RequestUtil.getDouble( request, "discount_percent", 100.00 );//商户折扣/%
        double discount_money = RequestUtil.getDouble( request, "discount_money", 1.00 );//商户折扣---每小时/元
        Integer validite_time = RequestUtil.getInteger( request, "validite_time", 24 );//有效期/小时
        Integer ticket_unit = RequestUtil.getInteger(request, "ticket_unit", 1);//单位
        double free_money = RequestUtil.getDouble(request, "free_money",1.00);//全免券单价---每张/元

//        if(validite_time==0||validite_time>500000){
//            return "{\"state\":0,\"msg\":\"请设置一个合理的有效期。\"}";
//        }

        //封装
        ShopTb shopTb = new ShopTb();
        shopTb.setName( name );
        shopTb.setAddress( address );
        shopTb.setMobile( mobile );
        shopTb.setTicketUnit( ticket_unit );
        //shopTb.setPhone( phone );
        shopTb.setTicketType( ticket_type );
        shopTb.setDefaultLimit( default_limit );
        shopTb.setDiscountMoney( new BigDecimal( discount_money ) );
        shopTb.setDiscountPercent( new BigDecimal( discount_percent ) );
        shopTb.setValiditeTime( validite_time );
        shopTb.setFreeMoney( new BigDecimal( free_money ) );
        shopTb.setHandInputEnable(handInputEnable);
        shopTb.setSupportType(supportType);
        shopTb.setUseLimit(useLimit);
        shopTb.setFreeLimitTimes(freeLimitTimes);
        shopTb.setUseFixCode(useFixCode);
        shopTb.setLimitDayUnit(limitDayUnit);

        shopTb.setComid( RequestUtil.getLong( request, "comid", -1L ) );
        int update = 0;

        ParkLogTb parkLogTb = new ParkLogTb();
        parkLogTb.setOperateUser(nickname);
        parkLogTb.setOperateTime(System.currentTimeMillis()/1000);
        parkLogTb.setType("shop");
        parkLogTb.setParkId(comid);

        if (id < 0) {
            //添加操作
            id = commonDao.selectSequence(ShopTb.class);
            Long create_time = System.currentTimeMillis() / 1000;
            shopTb.setCreateTime( create_time );
            shopTb.setId(id);
            update = commonDao.insert( shopTb );
            parkLogTb.setOperateType(1);
            parkLogTb.setContent(uin+"("+nickname+")"+"增加了商户"+id+name);

        } else {
            //修改操作
            shopTb.setId( id );
            update = commonDao.updateByPrimaryKey( shopTb );
            parkLogTb.setOperateType(2);
            parkLogTb.setContent(uin+"("+nickname+")"+"修改了商户"+id);
        }
        saveLogService.saveLog(parkLogTb);
        return "{\"state\":" + update + "}";
    }
}
