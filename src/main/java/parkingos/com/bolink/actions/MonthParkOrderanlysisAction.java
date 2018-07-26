package parkingos.com.bolink.actions;


import com.alibaba.fastjson.JSONObject;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import parkingos.com.bolink.models.ParkLogTb;
import parkingos.com.bolink.service.MonthParkOrderAnlysisService;
import parkingos.com.bolink.service.SaveLogService;
import parkingos.com.bolink.utils.ExportExcelUtil;
import parkingos.com.bolink.utils.RequestUtil;
import parkingos.com.bolink.utils.StringUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.text.ParseException;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/monthparkorder")
public class MonthParkOrderanlysisAction {

    Logger logger = Logger.getLogger(MonthParkOrderanlysisAction.class);

    @Autowired
    private MonthParkOrderAnlysisService monthparkOrderanlysisService;

    @Autowired
    private SaveLogService saveLogService;


    /*
    * 车场 统计分析  车场月报
    *
    * */
    @RequestMapping(value = "/query")
    public String query(HttpServletRequest request, HttpServletResponse resp) throws Exception{

        Map<String, String> reqParameterMap = RequestUtil.readBodyFormRequset(request);

        JSONObject result = monthparkOrderanlysisService.selectResultByConditions(reqParameterMap);
        //把结果返回页面
        StringUtils.ajaxOutput(resp,result.toJSONString());
        return null;
    }

    @RequestMapping(value = "/exportExcel")
    public String exportExcel(HttpServletRequest request, HttpServletResponse response) throws Exception{
        Long comid = RequestUtil.getLong(request,"comid",-1L);
        String nickname1 = StringUtils.decodeUTF8(RequestUtil.getString(request,"nickname1"));
        Long uin = RequestUtil.getLong(request, "loginuin", -1L);
        Map<String, String> reqParameterMap = RequestUtil.readBodyFormRequset(request);

        List<List<Object>> resList = monthparkOrderanlysisService.exportExcel(reqParameterMap);
        String title = "车场月报";
        String sheeatName = "sheet1";
        String headers[] =  { "日期", "订单总数", "应收金额", "实收金额", "实收金额", "实收金额","减免金额" } ;
        String dataType []={"STR","STR","STR","STR","STR","STR","STR"};
        String[] subHeads = new String[] {"现金支付", "电子支付", "合计"};
        String[] headnum = new String[] { "1,2,0,0", "1,2,1,1","1,2,2,2","1,1,3,5","1,2,6,6"};
        String[] subheadnum = new String[] { "2,2,3,3", "2,2,4,4", "2,2,5,5"};
        ExportExcelUtil excelUtil = new ExportExcelUtil(title, headers, sheeatName, dataType, subHeads, headnum, subheadnum, new int[]{2,6});
        String fname = "车场月报";
        fname = StringUtils.encodingFileName(fname)+".xls";
        try {
            OutputStream os = response.getOutputStream();
            response.reset();
            response.setHeader("Content-disposition", "attachment; filename="+fname);
            excelUtil.PoiWriteExcel_To2007(resList,os);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ParseException e) {
            e.printStackTrace();
        }
        ParkLogTb parkLogTb = new ParkLogTb();
        parkLogTb.setOperateUser(nickname1);
        parkLogTb.setOperateTime(System.currentTimeMillis()/1000);
        parkLogTb.setOperateType(4);
        parkLogTb.setContent(uin+"("+nickname1+")"+"导出了车场月报");
        parkLogTb.setType("order");
        parkLogTb.setParkId(comid);
        saveLogService.saveLog(parkLogTb);

        return null;
    }

}
