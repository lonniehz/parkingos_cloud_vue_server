package parkingos.com.bolink.actions;


import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import parkingos.com.bolink.models.ParkLogTb;
import parkingos.com.bolink.service.CityUnorderService;
import parkingos.com.bolink.service.SaveLogService;
import parkingos.com.bolink.utils.ExportDataExcel;
import parkingos.com.bolink.utils.RequestUtil;
import parkingos.com.bolink.utils.StringUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.text.ParseException;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/unorder")
public class CityUnorderAction {

    Logger logger = LoggerFactory.getLogger(CityUnorderAction.class);

    @Autowired
//    @Resource(name = "citymybatis")
    @Resource(name = "unorderSpring")
    private CityUnorderService cityUnorderService;
    @Autowired
    private SaveLogService saveLogService;

    /*
    * 集团和城市在场车辆接口
    *
    * */
    @RequestMapping(value = "/query")
    public String query(HttpServletRequest request, HttpServletResponse resp) {

        Map<String, String> reqParameterMap = RequestUtil.readBodyFormRequset(request);

        JSONObject result = cityUnorderService.selectResultByConditions(reqParameterMap);
        //把结果返回页面
        StringUtils.ajaxOutput(resp, result.toJSONString());
        return null;
    }

    @RequestMapping(value = "/exportExcel")
    public String exportExcel(HttpServletRequest request, HttpServletResponse response) {
        Long groupid = RequestUtil.getLong(request,"groupid",-1L);
        String nickname = StringUtils.decodeUTF8(RequestUtil.getString(request,"nickname1"));
        Long uin = RequestUtil.getLong(request, "loginuin", -1L);

        Map<String, String> reqParameterMap = RequestUtil.readBodyFormRequset(request);

        List<List<Object>> bodyList = cityUnorderService.exportExcel(reqParameterMap);
        String [][] heards = new String[][]{{"编号","STR"},{"所属车场","STR"},{"进场收费员","STR"},{"进场方式","STR"},{"车牌号","STR"},{"进场时间","STR"},{"停车时长","STR"},{"状态","STR"},{"进场通道","STR"},{"车场订单编号","STR"}};
        ExportDataExcel excel = new ExportDataExcel("在场车辆数据", heards, "sheet1");
        String fname = "在场车辆数据";
        fname = StringUtils.encodingFileName(fname)+".xls";
        try {
            OutputStream os = response.getOutputStream();
            response.reset();
            response.setHeader("Content-disposition", "attachment; filename="+fname);
            excel.PoiWriteExcel_To2007(bodyList, os);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ParseException e) {
            e.printStackTrace();
        }
        ParkLogTb parkLogTb = new ParkLogTb();
        parkLogTb.setOperateUser(nickname);
        parkLogTb.setOperateTime(System.currentTimeMillis()/1000);
        parkLogTb.setOperateType(4);
        parkLogTb.setContent(uin+"("+nickname+")"+"导出了在场车辆数据");
        parkLogTb.setType("order");
        parkLogTb.setGroupId(groupid);
        saveLogService.saveLog(parkLogTb);

        return null;
    }


    @RequestMapping(value = "/tozero")
    public String toZero(HttpServletRequest request, HttpServletResponse resp) {
        try {

            String nickname = StringUtils.decodeUTF8(RequestUtil.getString(request,"nickname1"));
            Long uin = RequestUtil.getLong(request, "loginuin", -1L);
            Long groupId = RequestUtil.getLong(request, "groupid", -1L);

            Long id = RequestUtil.getLong(request, "id", -1L);
            Long cityid = RequestUtil.getLong(request, "cityid", -1L);
            Long createTime = RequestUtil.getLong(request, "in_time", -1L);
            String money = RequestUtil.getString(request,"money");

            logger.info("==>>>>0元结算:"+id+"~"+createTime+"~"+money+"~"+uin+"~"+groupId+"~"+nickname);

            JSONObject result = cityUnorderService.toZero(id, cityid, createTime,money,groupId,uin,nickname);
            //把结果返回页面
            StringUtils.ajaxOutput(resp, result.toJSONString());
        }catch (Exception e){
            logger.error("零元结算异常",e);
        }
        return null;
    }

}
