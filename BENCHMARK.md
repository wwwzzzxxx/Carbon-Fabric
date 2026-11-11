# Benchmark / 实测

RTX 5060 Ti native OpenGL, 1280×720, a 1.5 GiB Java heap, and an eight-core JVM budget. A single chunk contains 3,000 ticking flint and steel drops. Entity shadows are enabled; shader packs are disabled. Sodium uses matching Iris and Lithium builds; OptiFine G8 uses a separate 1.16.5 profile. Java is 21.0.12.1 through Minecraft 1.21.1 and 25.0.4.1 from 1.21.3 onward. Values are average FPS / 1% low.

RTX 5060 Ti 原生 OpenGL，1280×720，1.5 GiB Java 堆，8 核 JVM 预算。单区块内 3,000 个正常 tick 的打火石，实体阴影开启、光影包关闭。Sodium 搭配对应版本的 Iris 和 Lithium；OptiFine G8 为独立的 1.16.5 配置。1.16.5 至 1.21.1 使用 Java 21.0.12.1，1.21.3 起使用 Java 25.0.4.1。数值均为平均 FPS / 1% low。

Each table identifies its measured Carbon suffix: `.14` means `carbon-<Minecraft>.9-fabric.14.jar`. The two experiments use different batches and builds. Compare conditions within a row; the tables are not a before-and-after comparison.

两张表分别标注实际测量的 Carbon 后缀，例如 `.14` 对应 `carbon-<Minecraft>.9-fabric.14.jar`。两组是不同批次、不同包的测试；性能对照限于各自表内的同一行，不能把两表数值直接当作改前改后。

## Seven conditions / 七组对照

The seven conditions compare the optimization stack without Carbon against the same stack with Carbon, with no item limit. Dependencies, game settings, and Java match within each row. Each condition is measured for eight seconds; the camera sweep spans 30 degrees in each direction. All 18 versions render 3,000 items facing the fixture and after returning; facing away submits no item models or shadows.

“无 Carbon”保留优化模组，“有 Carbon”在相同环境中加入 Carbon；同一行的依赖、游戏设置和 Java 一致，均不限额。每种工况采样 8 秒，扫视-覆盖左右各 30°。

| Minecraft | Carbon suffix | Empty, no Carbon | Front, no Carbon | Front, Carbon | Sweep, no Carbon | Sweep, Carbon | Back, no Carbon | Back, Carbon |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| 1.16.5 | .11 | 1799.4 / 728.6 | 55.3 / 33.4 | 1125.8 / 323.5 | 55.2 / 40.4 | 984.9 / 292.5 | 1370.1 / 421.3 | 1644.3 / 399.0 |
| 1.17.1 | .6 | 2014.8 / 792.8 | 65.5 / 40.1 | 1233.1 / 311.2 | 63.9 / 40.5 | 934.7 / 289.0 | 1227.6 / 293.9 | 1671.5 / 347.5 |
| 1.18.2 | .12 | 1483.5 / 343.4 | 62.1 / 34.5 | 1105.5 / 331.8 | 60.0 / 35.1 | 854.0 / 310.8 | 1079.4 / 280.6 | 1255.6 / 293.6 |
| 1.19.4 | .5 | 1988.4 / 689.1 | 70.1 / 36.4 | 1199.1 / 303.1 | 68.8 / 40.0 | 955.5 / 308.2 | 1593.3 / 197.8 | 1670.4 / 333.2 |
| 1.20.1 | .5 | 1956.7 / 656.6 | 73.1 / 44.7 | 1150.6 / 257.1 | 70.6 / 44.7 | 861.2 / 246.8 | 1355.9 / 258.5 | 1702.0 / 299.2 |
| 1.20.2 | .5 | 1728.6 / 525.9 | 72.6 / 42.4 | 1154.0 / 262.8 | 70.7 / 46.9 | 810.4 / 233.4 | 1202.5 / 199.2 | 1645.9 / 294.1 |
| 1.20.4 | .6 | 1771.0 / 619.1 | 74.5 / 44.5 | 1096.2 / 260.0 | 69.6 / 48.0 | 813.3 / 234.2 | 1305.8 / 266.7 | 1609.7 / 290.3 |
| 1.20.6 | .5 | 1777.0 / 530.5 | 72.8 / 54.8 | 1090.6 / 260.1 | 75.0 / 49.9 | 875.6 / 269.4 | 1303.7 / 309.3 | 1526.8 / 312.2 |
| 1.21.1 | .5 | 1904.4 / 661.6 | 66.1 / 44.7 | 1283.8 / 277.4 | 66.5 / 45.7 | 987.5 / 252.4 | 1183.2 / 284.1 | 1611.6 / 283.2 |
| 1.21.3 | .4 | 1729.0 / 522.3 | 69.7 / 42.8 | 1285.3 / 289.4 | 69.6 / 47.3 | 983.0 / 262.7 | 1231.1 / 268.3 | 1662.6 / 315.1 |
| 1.21.4 | .5 | 1885.2 / 598.1 | 68.8 / 45.1 | 1150.3 / 255.1 | 68.5 / 43.6 | 713.3 / 212.7 | 1207.5 / 257.4 | 1635.6 / 301.7 |
| 1.21.5 | .11 | 1628.4 / 571.8 | 59.9 / 34.5 | 988.9 / 250.8 | 59.8 / 42.6 | 939.4 / 231.7 | 1111.7 / 269.2 | 1184.7 / 283.5 |
| 1.21.8 | .9 | 1446.0 / 490.9 | 80.1 / 45.1 | 993.9 / 228.2 | 79.8 / 46.8 | 934.4 / 236.8 | 1023.0 / 272.8 | 1057.1 / 244.7 |
| 1.21.10 | .8 | 2980.2 / 764.8 | 91.4 / 56.3 | 1106.5 / 285.8 | 91.8 / 60.2 | 1113.3 / 290.2 | 1867.2 / 360.5 | 1751.3 / 348.0 |
| 1.21.11 | .8 | 2849.9 / 897.6 | 71.7 / 42.6 | 1130.2 / 321.1 | 66.7 / 48.0 | 1108.7 / 336.7 | 1741.9 / 401.3 | 1679.8 / 379.3 |
| 26.1.2 | .8 | 2688.5 / 860.7 | 79.0 / 51.2 | 1012.0 / 245.2 | 79.0 / 53.2 | 977.9 / 247.7 | 1849.4 / 357.0 | 1715.5 / 321.8 |
| 26.2 | .8 | 3302.2 / 971.4 | 76.7 / 52.1 | 1262.3 / 302.4 | 76.0 / 53.5 | 1093.5 / 268.8 | 2108.3 / 344.1 | 1887.1 / 328.9 |
| 26.3 | .5 | 3667.3 / 1000.6 | 90.7 / 56.6 | 1092.1 / 230.2 | 90.4 / 58.8 | 1140.0 / 268.2 | 1791.7 / 281.0 | 2005.9 / 332.2 |

## Tweakeroo limits / 限额对照

Carbon is enabled throughout this table, with matching Tweakeroo / MaLiLib builds. Carbon selects visible items using the configured Tweakeroo limit. One client samples unlimited, 4096, 512, 512, 4096, then unlimited, for four seconds per window. Unlimited and 4096 draw 3,000 items; 512 draws 512. All 19 configurations submit no item models or shadows at limit zero or when facing away, then restore 512 items on returning. Tweakeroo native item counters remain zero in the capped front-facing samples.

本表全部开启 Carbon，并安装对应版本的 Tweakeroo / MaLiLib。Carbon 按 Tweakeroo 设置的上限自行筛选可见实体。同一客户端按“不限额、4096、512、512、4096、不限额”采样，每档两段、每段 4 秒；不限额和 4096 档实际绘制 3,000 个，512 档绘制 512 个。

| Minecraft | Carbon suffix | Unlimited | 4096 limit | 512 limit |
| --- | ---: | ---: | ---: | ---: |
| 1.16.5 Sodium | .14 | 981.3 / 224.9 | 996.4 / 225.6 | 1124.5 / 236.4 |
| 1.16.5 OptiFine G8 | .14 | 699.7 / 77.1 | 698.1 / 76.7 | 814.0 / 77.2 |
| 1.17.1 | .8 | 3265.5 / 533.6 | 3371.6 / 539.0 | 3419.2 / 564.4 |
| 1.18.2 | .14 | 1260.8 / 315.7 | 1306.4 / 320.4 | 1589.1 / 333.1 |
| 1.19.4 | .7 | 3245.7 / 506.9 | 3380.6 / 537.5 | 3385.0 / 551.7 |
| 1.20.1 | .7 | 3233.4 / 453.3 | 3131.6 / 461.7 | 3255.9 / 474.8 |
| 1.20.2 | .7 | 3372.4 / 484.1 | 3341.3 / 495.3 | 3493.7 / 521.0 |
| 1.20.4 | .8 | 1213.9 / 266.4 | 1220.2 / 274.7 | 1553.0 / 288.0 |
| 1.20.6 | .7 | 3223.0 / 445.5 | 3252.0 / 468.9 | 3426.0 / 493.4 |
| 1.21.1 | .7 | 3210.2 / 418.6 | 3279.3 / 441.9 | 3444.7 / 461.8 |
| 1.21.3 | .6 | 3227.0 / 420.3 | 3321.6 / 447.5 | 3308.1 / 458.8 |
| 1.21.4 | .7 | 1308.5 / 290.8 | 1309.9 / 287.7 | 1648.7 / 309.6 |
| 1.21.5 | .13 | 2405.6 / 351.3 | 2373.9 / 342.0 | 2533.6 / 364.2 |
| 1.21.8 | .11 | 2102.8 / 304.2 | 1882.6 / 291.9 | 2098.6 / 317.2 |
| 1.21.10 | .10 | 651.3 / 199.9 | 666.1 / 212.5 | 1175.4 / 267.3 |
| 1.21.11 | .10 | 1128.0 / 315.6 | 1148.0 / 325.8 | 1909.8 / 369.3 |
| 26.1.2 | .10 | 1097.5 / 250.7 | 1126.3 / 260.2 | 1844.9 / 330.6 |
| 26.2 | .10 | 1150.4 / 258.7 | 1195.3 / 288.9 | 2139.6 / 333.9 |
| 26.3 | .7 | 699.1 / 192.1 | 719.9 / 219.8 | 1290.4 / 272.6 |

19 套配置均验收：0 限额和背向时模型、阴影提交为零，转回恢复 512 个。有限额的正对样本里，Tweakeroo 原生掉落物计数保持为零。
