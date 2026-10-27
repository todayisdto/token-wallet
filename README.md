# 词元钱包 · Token Wallet

一个很小的安卓桌面小组件：**同时显示 DeepSeek 和阿里云账户余额**。

- 4×2 和 2×2 两种桌面小组件，两家余额一眼看完
- 底部两个标签：账户 / 设置，外观设置放在设置页
- 界面和小组件支持「跟随系统 / 浅色 / 深色」
- 账户设置默认收起，点标题展开
- 后台自动刷新（30 分钟 / 1 小时 / 3 小时 / 6 小时）
- 设置页可关闭电池优化，也可选择是否隐藏最近任务卡片
- Key 只加密保存在手机本地，不上传任何服务器
- 不需要服务器，不需要注册账号

---

## 1. 下载安装

1. 打开 [Releases](https://github.com/todayisdto/token-wallet/releases/latest)
2. 下载最新版本的 `app-release.apk`
3. 传到手机后点开安装
4. 如果提示「禁止安装未知应用」，允许当前文件管理器安装
5. 安装完成后打开「词元钱包」

> 本项目是个人侧载应用，不在应用商店上架。请只从本仓库的 Releases 下载 APK。

### 添加桌面小组件

1. 长按桌面空白处
2. 选择「小组件」/「窗口小工具」
3. 找到「词元钱包」
4. 长按拖到桌面，可选 4×2 横版或 2×2 方版

---

## 2. 首次配置

使用前需要准备两样东西：

- DeepSeek API Key
- 阿里云 **只读 RAM 子账号**的 AccessKey

### 2.1 DeepSeek

1. 打开 <https://platform.deepseek.com/api_keys>
2. 登录后点「创建 API key」
3. 名字随便填，例如 `token-wallet`
4. 复制以 `sk-` 开头的 Key（只显示一次）
5. 回到 App，展开 DeepSeek，粘贴后点「测试并保存」

查余额接口不消耗 token，可以放心刷新。

### 2.2 阿里云

> **不要用阿里云主账号的 AccessKey。** 主账号权限太大，手机丢失会很危险。

1. 登录阿里云控制台，点右上角头像 →「访问控制」（RAM）
2. 左侧「身份管理」→「用户」→「创建用户」
3. 登录名称填 `token-wallet`，显示名称填「余额小组件」
4. 访问方式勾选 **「OpenAPI 调用访问」**（不要勾控制台访问）
5. 创建后复制 **AccessKey ID** 和 **AccessKey Secret**（Secret 只显示一次）
6. 回到用户列表，点进刚创建的用户 →「权限管理」→「新增授权」
7. 搜索并选中 **`AliyunBSSReadOnlyAccess`**（费用与成本只读）→ 确定
8. 等 1～2 分钟，回到 App，展开阿里云，填 ID / Secret，点「测试并保存」

如果提示 `NoPermission`：

- 确认授权的是 `AliyunBSSReadOnlyAccess`
- 确认填的是 RAM 子账号的 Key，不是主账号的
- 退出重进 App 再试一次

更细的权限说明：[阿里云 API 调用授权](https://help.aliyun.com/zh/user-center/developer-reference/api-calling-authorization)。

---

## 3. 使用说明

- **测试并保存**：先验证 Key 能不能用，成功后才保存；失败不会覆盖旧 Key
- **账户设置**：点「DeepSeek」或「阿里云」标题展开，保存后自动收起
- **外观**：设置页顶部「外观」，选择「跟随系统 / 浅色 / 深色」；App 和小组件一起切换
- **刷新频率**：30 分钟 / 1 小时 / 3 小时 / 6 小时；系统可能延迟，手动刷新始终可用
- **显示内容**：
  - DeepSeek：总余额，副标题显示赠金 / 充值
  - 阿里云：可用额度，副标题显示现金 / 信用额度
- **失败时**：保留上一次成功的数字，副标题显示红色错误原因
- **隐藏最近任务卡片**：设置页默认关闭；开启后离开 App 会从最近任务移除，关掉开关即可恢复正常

### 提高后台刷新成功率

国产 ROM 可能会限制后台，建议：

1. App 里点「设置」→「关闭电池优化」，把「词元钱包」设为「不限制」
2. 手机设置里允许「自启动」/「后台运行」
3. 如果还是不刷新，点小组件上的刷新按钮，或打开 App 点「立即刷新全部」

---

## 4. 安全说明

- DeepSeek Key 和阿里云 AccessKey 使用 Android Keystore 加密后保存在本机
- 关闭了系统备份，Key 不会被备份到云端
- App 没有任何服务器、统计 SDK 或遥测
- 阿里云只读子账号只能查账单，不能操作服务器、域名、数据库
- 手机丢失：去阿里云 RAM 控制台禁用该 AccessKey，去 DeepSeek 平台删除 API Key
- 不要把 Key 写进 GitHub、截图、聊天记录

---

## 5. 常见问题

### 阿里云返回 0.00

先看阿里云控制台「费用与成本」里的实际余额。如果控制台有余额但 App 是 0，通常是 RAM 权限没生效，等 1～2 分钟或重新授权。

### `SignatureDoesNotMatch`

AccessKey Secret 填错了，或者复制时多了空格。重新创建一对 AccessKey 再试。

### `InvalidTimeStamp.Expired`

手机时间不准。打开「设置 → 日期和时间 → 自动设置」再试。

### 小组件一直显示「尚未更新」

打开 App 点「立即刷新全部」。如果 App 里能刷新、小组件不更新，检查是否关闭了电池优化，以及桌面小组件是否被系统冻结。

---

## 6. 从源码构建（开发者）

需要 JDK 17 和 Android SDK 35。

```bash
git clone https://github.com/todayisdto/token-wallet.git
cd token-wallet
./gradlew :app:testDebugUnitTest :app:assembleRelease
```

Windows 用 `gradlew.bat`。产物在：

```text
app/build/outputs/apk/release/app-release.apk
```

签名 key 不包含在仓库里。没有 key 时，构建会使用 debug 签名，适合自己测试；这种测试包不能覆盖官方 APK。

---

## 7. 开发说明 / 致谢

本项目是在 **DeepSeek Harness** 的协助下完成的。代码实现、界面调整、问题排查和 GitHub Actions 打包流程主要由 AI 生成；项目所有者负责提出需求、试用反馈、确认视觉效果和最终发布。

---

## 8. License

MIT。见 [LICENSE](LICENSE)。

DeepSeek、阿里云是其各自公司的商标；本项目与两家公司没有关联。
