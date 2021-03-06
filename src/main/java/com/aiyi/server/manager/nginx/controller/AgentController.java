package com.aiyi.server.manager.nginx.controller;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import javax.annotation.Resource;
import javax.validation.ValidationException;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import com.aiyi.server.manager.nginx.bean.NginxConfig;
import com.aiyi.server.manager.nginx.bean.TableDate;
import com.aiyi.server.manager.nginx.bean.nginx.NginxUpstream;
import com.aiyi.server.manager.nginx.bean.nginx.NginxUpstreamItem;
import com.aiyi.server.manager.nginx.bean.result.Result;
import com.aiyi.server.manager.nginx.common.CommonFields;
import com.aiyi.server.manager.nginx.common.NginxUtils;
import com.aiyi.server.manager.nginx.conf.Configer;
import com.aiyi.server.manager.nginx.manager.NginxManager;
import com.aiyi.server.manager.nginx.utils.Vali;
import com.github.odiszapc.nginxparser.NgxBlock;
import com.github.odiszapc.nginxparser.NgxComment;
import com.github.odiszapc.nginxparser.NgxConfig;
import com.github.odiszapc.nginxparser.NgxDumper;
import com.github.odiszapc.nginxparser.NgxEntry;
import com.github.odiszapc.nginxparser.NgxParam;
import com.github.odiszapc.nginxparser.NgxToken;

/**
 * 反向代理控制台
 * @Project : nginx-gui
 * @Program Name : com.aiyi.server.manager.nginx.controller.AgentController.java
 * @Description : 
 * @Author : 郭胜凯
 * @Creation Date : 2018年2月22日 下午5:44:38
 * @ModificationHistory Who When What ---------- ------------- -----------------------------------
 *                      郭胜凯 2018年2月22日 create
 */
@Controller
@RequestMapping("admin/agent")
public class AgentController {

  @Resource
  private NginxManager nginxManager;
  
  /**
   * 反向代理管理页面
   * @Description : 
   * @return : String
   * @Creation Date : 2018年2月7日 下午5:01:43
   * @Author : 郭胜凯
   */
  @RequestMapping("back")
  public String back() {
    return "admin/agent/back/index";
  }
  
  /**
   * 
   * @Description : 
   * @return : String
   * @throws IOException 
   * @Creation Date : 2018年2月22日 下午5:42:46
   * @Author : 郭胜凯
   */
  @RequestMapping("back/edit/{value}")
  public String edit(@PathVariable String value, Model model) throws IOException {
	  if(!Vali.isEpt(value)) {
		  for (NginxUpstream nginxUpstream : listUpstreams(NginxUtils.read())) {
			if (!Vali.isEpt(nginxUpstream.getValue()) && value.equals(nginxUpstream.getValue())) {
				model.addAttribute("upstream", nginxUpstream);
				break;
			}
		  }
	  }
	  return "admin/agent/back/edit";
  }
  
  /**
   * 保存负载配置
   * @Description : 
   * @return : Result
   * @Creation Date : 2018年2月23日 下午6:56:00
   * @Author : 郭胜凯
   */
  @RequestMapping(value = "back/save", method = RequestMethod.PUT)
  @ResponseBody
  public Result save(@RequestBody NginxUpstream upstream) {
	  if (Vali.isEpt(upstream)) {
		  throw new ValidationException("请输入正确的配置");
	  }
	  if (Vali.isEpt(upstream.getDesp()) || Vali.isEpt(upstream.getValue())) {
		  throw new ValidationException("负载描述和负载名称不能为空");
	  }
	  if(upstream.getItemsLength() < 1) {
		  throw new ValidationException("至少要配置一个节点地址");
	  }
	  Result result = new Result();
	  result.setCode(CommonFields.ERROR_CODE.SERVER);
	  result.setMessage("未找到目标配置信息");
	  NgxConfig conf = NginxUtils.read();
	  String backConf = NginxUtils.toString(conf);
	  NgxEntry old = null;
	  List<NgxEntry> findAll = conf.findAll(NgxConfig.BLOCK, "http", "upstream");
	    for (NgxEntry ngxEntry : findAll) {
	    		//定位负载配置
	    		if (upstream.getValue().equals(((NgxBlock)ngxEntry).getValue())) {
	    			old = ngxEntry;
	    			break;
	    		}
	    		
		}
	    System.out.println(upstream);
	    //更新Conf
	    addUpstream(conf, upstream, old);
	    //尝试写到Nginx配置文件
		try {
			NginxUtils.save(conf);
			//重启Nginx
			nginxManager.reload();
			result.setCode("SUCCESS");
			result.setSuccess(true);
		} catch (Exception e) {
			//恢复到上一次配置
			NginxUtils.save(backConf);
			throw new ValidationException("已恢复到上一次配置:" + e.getMessage());
		}
		return result;
  }
  
  @RequestMapping(value = "back/del/{value}", method = RequestMethod.PUT)
  @ResponseBody
  public String del(@PathVariable String value) {
	if (Vali.isEpt(value)) {
		throw new ValidationException("不能传递空的upstream值");
	}
	NgxConfig conf = NginxUtils.read();
	  String backConf = NginxUtils.toString(conf);
	  List<NgxEntry> findAll = conf.findAll(NgxConfig.BLOCK, "http", "upstream");
	    for (NgxEntry ngxEntry : findAll) {
	    		//定位负载配置
	    		if (value.equals(((NgxBlock)ngxEntry).getValue())) {
	    			conf.remove(ngxEntry);
	    			break;
	    		}
		}
	    //尝试写到Nginx配置文件
		try {
			NginxUtils.save(conf);
			//重启Nginx
			nginxManager.reload();
		} catch (Exception e) {
			//恢复到上一次配置
			NginxUtils.save(backConf);
			throw new ValidationException("已恢复到上一次配置:" + e.getMessage());
		}
	return "success";
  }
  
  /**
   * 获取反向代理配置列表的前段接收Json
   * @Description : 
   * @return : List<NginxConfig>
   * @throws IOException 
   * @Creation Date : 2018年2月7日 下午5:06:08
   * @Author : 郭胜凯
   */
  @RequestMapping("back/list")
  @ResponseBody
  public TableDate listBack(int start, int length) throws IOException{
    TableDate tableDate = new TableDate();
    List<NginxUpstream> result = listUpstreams(NginxUtils.read());
    tableDate.setList(result);
    return tableDate;
  }
  
  private void addUpstream(NgxConfig conf, NginxUpstream upstream, NgxEntry ngxEntry) {
	NgxBlock newBlock = new NgxBlock();
	newBlock.addEntry(new NgxComment(" " + upstream.getDesp()));
	for (NginxUpstreamItem item : upstream.getItems()) {
		NgxParam param = new NgxParam();
		param.addValue("server " + item.getAddress());
		param.addValue("weight=" + item.getWeight());
		newBlock.addEntry(param);
	}
	newBlock.addValue(new NgxToken(upstream.getName()));
	newBlock.addValue(new NgxToken(upstream.getValue()));
	
	NgxBlock findBlock = conf.findBlock("http");
	conf.remove(findBlock);
	if (null != ngxEntry) {
		findBlock.remove(ngxEntry);
	}
	findBlock.addEntry(newBlock);
	conf.addEntry(findBlock);
	
  }
  
  /**
   * 获得反向代理配置列表
   * @Description : 
   * @return : List<NginxUpstream>
   * @Creation Date : 2018年2月22日 下午6:27:32
   * @Author : 郭胜凯
   */
  public List<NginxUpstream> listUpstreams(NgxConfig conf){
	    List<NgxEntry> findAll = conf.findAll(NgxConfig.BLOCK, "http", "upstream");
	    List<NginxUpstream> result = new ArrayList<>();
	    for (NgxEntry ngxEntry : findAll) {
		    	NginxUpstream upstream = new NginxUpstream();
		    	upstream.setItems(new ArrayList<>());
	    		upstream.setName(((NgxBlock)ngxEntry).getName());
	    		String value = ((NgxBlock)ngxEntry).getValue();
	    		upstream.setValue(value);
	    		((NgxBlock)ngxEntry).forEach((a) ->{
	    			if(a instanceof NgxComment) {
	    				upstream.setDesp(((NgxComment) a).getValue());
	    			}else {
	    				NgxParam param = (NgxParam) a;
	    				NginxUpstreamItem item = new NginxUpstreamItem();
	    				List<String> values = param.getValues();
	    				item.setAddress(values.get(0));
	    				if (values.size() > 1) {
	    					item.setWeight(Integer.parseInt(values.get(1).replace("weight=", "").trim()));
					}
	    				upstream.getItems().add(item);
	    			}
	    		});
	    		if (null == upstream.getDesp()) {
	    			upstream.setDesp(UUID.randomUUID().toString().replace("-", ""));
	    			((NgxBlock)ngxEntry).addEntry(new NgxComment(upstream.getDesp()));
	    			NginxUtils.save(conf);
			}
	    		result.add(upstream);
	    		System.out.println(upstream);
		}
	    return result;
  }
}
