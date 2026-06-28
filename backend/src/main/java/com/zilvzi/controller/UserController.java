package com.zilvzi.controller;

import com.zilvzi.pojo.Result;
import com.zilvzi.pojo.User;
import com.zilvzi.pojo.Goal;
import com.zilvzi.pojo.Partner;
import com.zilvzi.service.UserService;
import com.zilvzi.mapper.GoalMapper;
import com.zilvzi.mapper.CheckinMapper;
import com.zilvzi.mapper.PartnerMapper;
import com.zilvzi.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户Controller
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private GoalMapper goalMapper;

    @Autowired
    private CheckinMapper checkinMapper;

    @Autowired
    private PartnerMapper partnerMapper;

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public Result<?> register(@RequestBody Map<String, String> params) {
        try {
            // 简单防刷：延迟200ms
            Thread.sleep(200);

            String username = params.get("username");
            String password = params.get("password");
            String phone = params.get("phone");
            String email = params.get("email");
            String nickname = params.get("nickname");
            String province = params.get("province");
            String city = params.get("city");
            String district = params.get("district");

            User user = userService.register(username, password, phone, email, nickname, province, city, district);

            // 生成Token
            String token = jwtUtil.generateToken(user.getId(), user.getUsername());

            Map<String, Object> result = new HashMap<>();
            result.put("token", token);
            result.put("userInfo", user);

            return Result.success("注册成功", result);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 用户登录（用户名+密码）
     */
    @PostMapping("/login")
    public Result<?> login(@RequestBody Map<String, String> params) {
        try {
            // 简单防刷：延迟200ms
            Thread.sleep(200);

            String username = params.get("username");
            String password = params.get("password");
            String loginType = params.get("loginType");

            User user;
            if ("phone".equals(loginType)) {
                String phone = params.get("phone");
                String code = params.get("code");
                user = userService.loginByPhone(phone, code);
            } else if ("email".equals(loginType)) {
                String email = params.get("email");
                String code = params.get("code");
                user = userService.loginByEmail(email, code);
            } else {
                user = userService.loginByUsername(username, password);
            }

            // 生成Token
            String token = jwtUtil.generateToken(user.getId(), user.getUsername());

            Map<String, Object> result = new HashMap<>();
            result.put("token", token);
            result.put("userInfo", user);

            return Result.success("登录成功", result);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 微信登录
     */
    @PostMapping("/login/wechat")
    public Result<?> wechatLogin(@RequestBody Map<String, String> params) {
        try {
            String code = params.get("code");
            String province = params.get("province");
            String city = params.get("city");
            String district = params.get("district");
            User user = userService.loginByWechat(code, province, city, district);

            // 生成Token
            String token = jwtUtil.generateToken(user.getId(), user.getUsername());

            Map<String, Object> result = new HashMap<>();
            result.put("token", token);
            result.put("userInfo", user);

            return Result.success("登录成功", result);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 发送验证码
     */
    @PostMapping("/sendCode")
    public Result<?> sendCode(@RequestBody Map<String, String> params) {
        try {
            String target = params.get("target");
            String type = params.get("type"); // phone 或 email

            userService.sendVerifyCode(target, type);
            return Result.success("验证码已发送");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取用户信息
     */
    @GetMapping("/info")
    public Result<?> getUserInfo(@RequestAttribute("userId") Long userId) {
        try {
            User user = userService.getUserById(userId);
            return Result.success(user);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取指定用户信息（脱敏）
     */
    @GetMapping("/info/{id}")
    public Result<?> getUserInfoById(@PathVariable Long id) {
        try {
            User user = userService.getUserById(id);
            if (user == null) {
                return Result.error("用户不存在");
            }
            // 脱敏处理：隐藏敏感信息
            user.setPhone(null);
            user.setEmail(null);
            user.setPassword(null);
            return Result.success(user);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 更新用户信息
     */
    @PutMapping("/info")
    public Result<?> updateUserInfo(@RequestAttribute("userId") Long userId, @RequestBody User user) {
        try {
            user.setId(userId);
            // 防止用户自行修改 level 和 experience
            user.setLevel(null);
            user.setExperience(null);
            userService.updateUser(user);
            return Result.success("更新成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取搭子详情（用户信息 + 目标 + 打卡统计）
     */
    @GetMapping("/detail/{id}")
    public Result<?> getUserDetail(@PathVariable Long id, @RequestAttribute("userId") Long currentUserId) {
        try {
            User user = userService.getUserById(id);
            if (user == null) {
                return Result.error("用户不存在");
            }
            System.out.println("[UserDetail] userId=" + id
                + " province=" + user.getProvince()
                + " city=" + user.getCity()
                + " district=" + user.getDistrict()
                + " nickname=" + user.getNickname());
            // 脱敏
            user.setPhone(null);
            user.setEmail(null);
            user.setPassword(null);

            // 目标列表（仅 active）
            List<Goal> allGoals = goalMapper.selectByUserId(id);
            List<Map<String, Object>> goals = new ArrayList<>();
            for (Goal g : allGoals) {
                if (!"active".equals(g.getStatus())) continue;
                Map<String, Object> gm = new HashMap<>();
                gm.put("id", g.getId());
                gm.put("name", g.getName());
                gm.put("type", g.getType());
                gm.put("description", g.getDescription());
                gm.put("streak", g.getStreak() != null ? g.getStreak() : 0);
                gm.put("totalCheckins", g.getTotalCheckins() != null ? g.getTotalCheckins() : 0);
                goals.add(gm);
            }

            // 打卡统计
            Integer totalDays = checkinMapper.countTotalCheckins(id);
            String earliestDate = checkinMapper.selectEarliestDate(id);
            double checkinRate = 0;
            if (earliestDate != null && totalDays != null && totalDays > 0) {
                long daysBetween = java.time.LocalDate.parse(earliestDate)
                        .until(java.time.LocalDate.now()).getDays() + 1;
                checkinRate = Math.round(totalDays * 100.0 / Math.max(daysBetween, 1));
            }

            // 查询当前用户与该用户的关系状态
            String relationStatus = "none";
            Partner relation = partnerMapper.selectByUserAndPartner(currentUserId, id);
            if (relation != null) {
                if ("accepted".equals(relation.getStatus())) relationStatus = "partner";
                else if ("pending".equals(relation.getStatus())) relationStatus = "pending";
                else if ("blocked".equals(relation.getStatus())) relationStatus = "blocked";
            }

            Map<String, Object> result = new HashMap<>();
            result.put("user", user);
            result.put("goals", goals);
            result.put("totalDays", totalDays != null ? totalDays : 0);
            result.put("checkinRate", checkinRate);
            result.put("relationStatus", relationStatus);

            return Result.success(result);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}