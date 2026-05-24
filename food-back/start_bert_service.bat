@echo off
echo 启动BERT NER服务...

REM 检查Python是否安装
python --version >nul 2>&1
if errorlevel 1 (
    echo 错误: 未找到Python，请先安装Python 3.7+
    pause
    exit /b 1
)

REM 检查依赖是否安装
echo 检查Python依赖...
python -c "import torch" >nul 2>&1
if errorlevel 1 (
    echo 安装PyTorch...
    pip install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cpu
)

python -c "import transformers" >nul 2>&1
if errorlevel 1 (
    echo 安装Transformers...
    pip install transformers
)

python -c "import flask" >nul 2>&1
if errorlevel 1 (
    echo 安装Flask...
    pip install flask
)

echo 启动BERT服务...
python bert_service.py

pause