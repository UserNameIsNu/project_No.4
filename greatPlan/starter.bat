@echo off
REM 保证从脚本所在位置开始执行
cd /d "%~dp0"

REM 进入 target 目录
cd target

REM 输出提示
echo 正在启动 GreatPlan，请稍候...

REM 启动 Spring Boot 应用
java -cp "classes;lib/*" com.ljf.greatplan.GreatPlanApplication

pause
