package com.example.seckill.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.seckill.config.AccessLimit;
import com.example.seckill.exception.GlobalException;
import com.example.seckill.pojo.Order;
import com.example.seckill.pojo.SeckillMessage;
import com.example.seckill.pojo.SeckillOrder;
import com.example.seckill.pojo.User;
import com.example.seckill.rabbitmq.MQSender;
import com.example.seckill.service.IGoodsService;
import com.example.seckill.service.IOrderService;
import com.example.seckill.service.ISeckillOrderService;
import com.example.seckill.service.impl.SeckillOrderServiceImpl;
import com.example.seckill.utils.JsonUtil;
import com.example.seckill.vo.GoodsVo;
import com.example.seckill.vo.RespBean;
import com.example.seckill.vo.RespBeanEnum;
import com.wf.captcha.ArithmeticCaptcha;
import com.wf.captcha.SpecCaptcha;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import javax.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀
 *
 * @author: LC
 * @date 2022/3/4 11:34 上午
 * @ClassName: SeKillController
 */
@Slf4j
@Controller
@RequestMapping("/seckill")
public class SeKillController implements InitializingBean {

    @Autowired
    private IGoodsService goodsService;

    @Autowired
    private ISeckillOrderService secKillOrderService;

    @Autowired
    private IOrderService orderService;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private MQSender mqSender;

    @Autowired
    private RedisScript<Long> redisScript;

    private Map<Long, Boolean> EmptyStockMap = new HashMap<>();


    @GetMapping(value = "/captcha")
    public void verifyCode(User user, Long goodsId, HttpServletResponse response) {
        if (user == null || goodsId < 0) {
            throw new GlobalException(RespBeanEnum.REQUEST_ILLEGAL);
        }
        //设置请求头为输出图片的类型
        response.setContentType("image/png");
        response.setHeader("Pargam", "No-cache");
        response.setHeader("Cache-Control", "no-cache");
        response.setDateHeader("Expires", 0);
        //生成验证码, 将结果放入Redis
        SpecCaptcha captcha = new SpecCaptcha(130, 48);
        // ArithmeticCaptcha captcha = new ArithmeticCaptcha(130, 32, 3);
        redisTemplate.opsForValue().set("captcha:" + user.getId() + ":" + goodsId, captcha.text(), 300, TimeUnit.SECONDS);
        try {
            captcha.out(response.getOutputStream());
        } catch (IOException e) {
            log.error("验证码生成失败", e.getMessage());
        }
    }

    @AccessLimit(second = 5, maxCount = 5, needLogin = true)
    @RequestMapping(value = "/path", method = RequestMethod.GET)
    @ResponseBody
    public RespBean getPath(User user, Long goodsId, String captcha, HttpServletRequest request) {
        if (user == null) {
            return RespBean.error(RespBeanEnum.SESSION_ERROR);
        }
//        ValueOperations valueOperations = redisTemplate.opsForValue();
//        // 限制访问次数，5秒内访问5次
//        String uri = request.getRequestURI();
//        captcha = "0";
//        Integer count = (Integer) valueOperations.get(uri + ":" + user.getId());
//        if (count == null) {
//            valueOperations.set(uri + ":" + user.getId(), 1, 5, TimeUnit.SECONDS);
//        } else if (count < 5) {
//            valueOperations.increment(uri + ":" + user.getId());
//        } else {
//            return RespBean.error(RespBeanEnum.ACCESS_LIMIT_REACHED);
//        }

        boolean check = orderService.checkCaptcha(user, goodsId, captcha);
        if (!check) {
            return RespBean.error(RespBeanEnum.ERROR_CAPTCHA);
        }
        String str = orderService.createPath(user, goodsId);
        return RespBean.success(str);
    }

    /**
     * 获取秒杀结果
     *
     * @param
     * @param goodsId
     * @return orderId 成功 ；-1 秒杀失败 ；0 排队中
     * @author LiChao
     * @operation add
     * @date 7:04 下午 2022/3/8
     **/
    @ApiOperation("获取秒杀结果")
    @GetMapping("getResult")
    @ResponseBody
    public RespBean getResult(User user, Long goodsId) {
        if (user == null) {
            return RespBean.error(RespBeanEnum.SESSION_ERROR);
        }
        Long orderId = secKillOrderService.getResult(user, goodsId);
        return RespBean.success(orderId);
    }

    @RequestMapping("/doSeckill")
    public String doSeckill2(Model model, User user, Long goodsId) {
        if (user == null) {
            return "login";
        }
        model.addAttribute("user", user);
        GoodsVo goodsVo = goodsService.findGoodsVoByGoodsId(goodsId);
        //判断库存
        if (goodsVo.getStockCount() < 1) {
            model.addAttribute("errmsg", RespBeanEnum.EMPTY_STOCK.getMessage());
            return "seckillFail";
        }
        // 判断是否重复抢购
        SeckillOrder seckillOrder = secKillOrderService.getOne(new QueryWrapper<SeckillOrder>()
                .eq("user_id", user.getId()).eq("goods_id", goodsId));
        if (seckillOrder != null) {
            model.addAttribute("errmsg", RespBeanEnum.REPEATE_ERROR.getMessage());
            return "seckillFail";
        }
        // 下订单
        Order order = orderService.seckill(user, goodsVo);
        model.addAttribute("order", order);
        model.addAttribute("goods", goodsVo);
        return "orderDetail";
    }

    @RequestMapping(value = "/{path}/doSeckill", method = RequestMethod.POST)
    @ResponseBody
    public RespBean doSeckill(@PathVariable String path, User user, Long goodsId) {
        if (user == null) {
            return RespBean.error(RespBeanEnum.SESSION_ERROR);
        }
        //优化后代码
        ValueOperations valueOperations = redisTemplate.opsForValue();
        boolean check = orderService.checkPath(user, goodsId, path);
        if (!check) {
            return RespBean.error(RespBeanEnum.REQUEST_ILLEGAL);
        }
        //判断是否重复抢购
        SeckillOrder seckillOrder = (SeckillOrder) redisTemplate.opsForValue().get("order:" + user.getId() + ":" + goodsId);
        if (seckillOrder != null) {
            return RespBean.error(RespBeanEnum.REPEATE_ERROR);
        }
        //内存标记，减少Redis的访问
        if (EmptyStockMap.get(goodsId)) {
            return RespBean.error(RespBeanEnum.EMPTY_STOCK);
        }
        //预减库存
        // Long stock = valueOperations.decrement("seckillGoods:" + goodsId);
        Long stock = (Long) redisTemplate.execute(redisScript, Collections.singletonList("seckillGoods:" + goodsId), Collections.EMPTY_LIST);
        if (stock < 0) {
            EmptyStockMap.put(goodsId, true);
            valueOperations.increment("seckillGoods:" + goodsId);
            return RespBean.error(RespBeanEnum.EMPTY_STOCK);
        }
        SeckillMessage seckillMessage = new SeckillMessage(user, goodsId);
        mqSender.sendSeckillMessage(JsonUtil.object2JsonStr(seckillMessage));
        return RespBean.success(0);
//        GoodsVo goodsVo = goodsService.findGoodsVoByGoodsId(goodsId);
//        //判断库存
//        if (goodsVo.getStockCount() < 1) {
//            model.addAttribute("errmsg", RespBeanEnum.EMPTY_STOCK.getMessage());
//            return RespBean.error(RespBeanEnum.EMPTY_STOCK);
//        }
//        // 判断是否重复抢购
////        SeckillOrder seckillOrder = secKillOrderService.getOne(new QueryWrapper<SeckillOrder>()
////                .eq("user_id", user.getId()).eq("goods_id", goodsId));
//        SeckillOrder seckillOrder = (SeckillOrder) redisTemplate.opsForValue().get("order:" + user.getId() + ":" + goodsId);
//        if (seckillOrder != null) {
//            model.addAttribute("errmsg", RespBeanEnum.REPEATE_ERROR.getMessage());
//            return RespBean.error(RespBeanEnum.REPEATE_ERROR);
//        }
//        // 下订单
//        Order order = orderService.seckill(user, goodsVo);
//        return RespBean.success(order);
    }

    /**
     * 系统初始化，把商品库存数量加载到Redis
     * @throws Exception
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        List<GoodsVo> list = goodsService.findGoodsVo();
        if (CollectionUtils.isEmpty(list)) {
            return;
        }
        list.forEach(goodsVo -> {
            redisTemplate.opsForValue().set("seckillGoods:" + goodsVo.getId(), goodsVo.getStockCount());
            EmptyStockMap.put(goodsVo.getId(), false); // 有库存
        });
    }
}
