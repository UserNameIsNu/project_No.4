import ollama
import time
import json
from ollama import chat

# 测试样例
# 定义一个类。
# 用于实现ollama连接，调用指定模型，流式输出，上下文管理，工具调用的综合案例。
class Demo:
    # 初始化
    # 像Java里面的构造方法，但合并了字段的定义（也能一并赋值）。
    # self必带，表示当前实例，就是这个类。
    # 先丢个参数进去，定义要用的模型
    def __init__(self, model="qwen2.5:3b"):
        # 模型
        self.model = model
        # 对话历史
        self.conversation_history = []
        # 模型可用的工具方法
        self.available_functions = self._define_functions()
        # 上下文配置
        self.context_config = {
            # 记忆的对话次数
            'max_history_length': 10,
            # 最大词元数
            'max_tokens': 10000,
            # 是否启用上下文记忆
            'enable_context': True
        }
        # 流式配置
        self.stream_config = {
            # 字符延迟
            'char_delay': 0.1,
            # 句子延迟
            'sentence_delay': 0.5,
            # 标点延迟
            'comma_delay': 0.1,
        }

        # 检查ollama连接
        self._check_ollama_service()

    # 检查ollama连接
    def _check_ollama_service(self):
        try:
            # 获取ollama的响应列表
            response = ollama.list()
            print("连接正常")
            # 获取模型列表
            model_num = 0
            for item in response.models:
                model_num += 1
                print(item)
            # 有没有拿到模型信息
            if model_num < 1:
                print("没有可用模型")
            return True
        except Exception as e:
            print(f"连接失败：{e}")
            return False

    # 可用工具方法定义
    def _define_functions(self):
        # 获取时间
        def get_time():
            return time.strftime("%Y-%m-%d %H:%M:%S", time.localtime())

        # 简单数学计算
        def calculate_math(expression: str):
            try:
                allowed_chars = set('0123456789+-*/.() ')
                if all(c in allowed_chars for c in expression):
                    result = eval(expression)
                    return f"计算结果: {expression} = {result}"
                else:
                    return "错误: 表达式包含不安全字符"
            except Exception as e:
                return f"计算错误: {e}"

        return {
            'get_time': get_time,
            'calculate_math': calculate_math
        }

    # 上下文管理
    def _manage_context(self, new_message):
        # 若未启用上下文管理
        if not self.context_config['enable_context']:
            # 直接退出
            return [{"role": "user", "content": new_message}]

        # 否则
        # 添加新对话至上下文
        self.conversation_history.append({"role": "user", "content": new_message})
        # 若当前上下文数量超出了限制
        if len(self.conversation_history) > self.context_config['max_history_length']:
            self.conversation_history = (
                # 保留第一条消息（初始提示词）
                self.conversation_history[:2] +
                # 加上从最新的开始数满最大限制数量的条数
                self.conversation_history[-(self.context_config['max_history_length'] - 2):]
            )

        return self.conversation_history.copy()

    # 系统消息管理
    def add_system_message(self, system_prompt):
        # 添加系统消息到对话开头
        system_message = {"role": "system", "content": system_prompt}
        # 若存在历史消息，且第一条为系统消息
        if self.conversation_history and self.conversation_history[0].get("role") == "system":
            # 替换现有系统消息
            self.conversation_history[0] = system_message
        else:
            # 插入新的系统消息
            self.conversation_history.insert(0, system_message)

    # 流式聊天（工具代理环路）
    def stream_chat(self, message):
        try:
            # 上下文管理
            current_messages = self._manage_context(message)

            # 工具调用链
            # 最终响应集
            final_response = ""
            while True:
                # 向模型发请求
                response = chat(
                     # 目标模型
                    model = self.model,
                    # 上下文历史
                    messages = current_messages,
                    # 可用工具方法
                    tools = [
                        {
                            "type": "function",
                            "function": {
                                "name": name,
                                "description": func.__doc__ or "",
                                "parameters": self._get_function_parameters(func)
                            }
                        } for name, func in self.available_functions.items()
                    ],
                    # 是否启动流式输出
                    stream = True
                )

                # 模型的响应
                # 是否需要调用工具方法
                tool_calls_detected = False
                # 需要哪些工具方法
                tool_calls = []
                # 模型回复
                full_response = ""

                # 检查模型的返回
                for chunk in response:
                    # 若模型返回的消息里表示需要调用工具方法，且给出了调用清单
                    if hasattr(chunk.message, 'tool_calls') and chunk.message.tool_calls:
                        # 标记需要调用工具方法
                        tool_calls_detected = True
                        # 保存调用清单
                        tool_calls = chunk.message.tool_calls

                    # 若模型返回的消息里有回复消息，且对应字段里确实有东西
                    if hasattr(chunk.message, 'content') and chunk.message.content:
                        # 获取内容
                        content = chunk.message.content
                        # 新建一个对象方法，用于流式输出回复
                        self._simulate_stream_output(content)
                        # 保存回复
                        full_response += content

                # 若模型觉得不需要调用工具
                if not tool_calls_detected:
                    final_response = full_response
                    self.conversation_history.append({"role": "assistant", "content": final_response})
                    break

                # 助理消息
                assistant_message = {
                    # 标记消息为助理
                    "role": "assistant",
                    # 消息内容
                    "content": full_response,
                    # 调用工具集
                    "tool_calls": [{
                            "function": {
                                "name": tool_call.function.name,
                                "arguments": tool_call.function.arguments
                            }
                        } for tool_call in tool_calls]
                }
                # 助理消息塞进上下文
                current_messages.append(assistant_message)
                # 也往对话历史里赛一份
                self.conversation_history.append(assistant_message)

                # 执行所有需要用的工具方法
                for tool_call in tool_calls:
                    # 获取工具方法名
                    tool_name = tool_call.function.name
                    # 获取工具方法参数
                    tool_args = tool_call.function.arguments

                    # 执行工具方法
                    # 若模型想用的工具方法在可用的工具方法列表中
                    if tool_name in self.available_functions:
                        try:
                            # 参数是否为字符串
                            # 参数不能是字符串json，要解析成字典
                            if isinstance(tool_args, str):
                                # 解析
                                parsed_args = json.loads(tool_args)
                            else:
                                # 不用管
                                parsed_args = tool_args

                            # 调用函数
                            # 因为目前只有获取时间的方法不用传参，所以先这么搞
                            # 后续应该改成适应任意参数的自动分发
                            if tool_name == 'get_time':
                                # 无参方法（获取时间）
                                tool_result = self.available_functions[tool_name]()
                            else:
                                # 有参方法，传入参数
                                tool_result = self.available_functions[tool_name](**parsed_args)
                            print(tool_result)

                            # 将工具结果添加到工具消息里
                            tool_message = {
                                # 工具类型的上下文消息
                                "role": "tool",
                                # 内容为方法的返回
                                "content": str(tool_result)
                            }
                            # 往上下文历史里面塞
                            current_messages.append(tool_message)
                            # 往对话历史里塞
                            self.conversation_history.append(tool_message)

                        except Exception as e:
                            error_msg = f"工具执行错误: {e}"
                            print(f"❌ {error_msg}")
                            # 错误信息也可一起塞进工具消息里
                            tool_message = {
                                # 消息类型和错误信息
                                "role": "tool",
                                "content": error_msg
                            }
                            # 记录进上下文（感觉一个上下文应该就够了呀）
                            current_messages.append(tool_message)
                            self.conversation_history.append(tool_message)
                    else:
                        error_msg = f"未知工具: {tool_name}"
                        print(f"❌ {error_msg}")
                        # 也是记录报错
                        tool_message = {
                            "role": "tool",
                            "content": error_msg
                        }
                        # 也是记录进来
                        current_messages.append(tool_message)
                        self.conversation_history.append(tool_message)
            # 返回调用结果
            return final_response
        except Exception as e:
            # 跟工具无关的报错，不是工具方法的锅
            error_msg = f"❌ 请求失败: {e}"
            print(error_msg)
            return error_msg

    # 标注所有工具方法的详情定义
    def _get_function_parameters(self, function):
        # 获取时间的方法
        if function.__name__ == 'get_time':
            # 返回类型，没有参数
            return {"type": "object", "properties": {}}
        # 简单算数方法
        elif function.__name__ == 'calculate_math':
            return {
                # 返回类型
                "type": "object",
                # 参数
                "properties": {
                    # 表达式：字符串类型，辅助描述
                    "expression": {"type": "string", "description": "数学表达式"}
                },
                # 必填：表达式
                "required": ["expression"]
            }
        # 获取天气的方法
        elif function.__name__ == 'get_weather':
            return {
                "type": "object",
                "properties": {
                    "city": {
                        "type": "string", "description": "城市名称"
                    }
                },
                "required": ["city"]
            }
        # 若试图调用不存在的方法
        return {
            # 直接返回空结构
            "type": "object",
            "properties": {}
        }

    # 流式输出（模拟）
    def _simulate_stream_output(self, content):
        # 按字符遍历字符串
        for char in content:
            # 输出这个字符
            print(char, end="", flush=True)
            # 若为句子，按句子末尾符号判断，睡眠延迟时间
            if char in '。！？.!?':
                time.sleep(self.stream_config['sentence_delay'])
            # 若为其它标点，同理
            elif char in '，,；;':
                time.sleep(self.stream_config['comma_delay'])
            # 否则就是单个字符延迟
            else:
                time.sleep(self.stream_config['char_delay'])

    # 清空上下文
    def clear_context(self, keep_system_message=True):
        # 若被标记为保存系统消息，且存在上下文记录
        if keep_system_message and self.conversation_history:
            # 提出系统消息
            system_messages = [msg for msg in self.conversation_history if msg.get("role") == "system"]
            # 用系统消息覆盖所有记录
            self.conversation_history = system_messages
        # 否则全部清空
        else:
            self.conversation_history = []

    # 展示上下文
    def show_context_info(self):
        # 所有上下文数量
        total_messages = len(self.conversation_history)
        # 用户消息数量
        user_messages = len([msg for msg in self.conversation_history if msg.get("role") == "user"])
        # 助手消息数量
        assistant_messages = len([msg for msg in self.conversation_history if msg.get("role") == "assistant"])
        # 系统消息数量
        system_messages = len([msg for msg in self.conversation_history if msg.get("role") == "system"])
        # 工具消息数量
        tool_messages = len([msg for msg in self.conversation_history if msg.get("role") == "tool"])

        print(f"\n📊 上下文信息:")
        print(f"   总消息数: {total_messages}")
        print(f"   用户消息: {user_messages}")
        print(f"   助手回复: {assistant_messages}")
        print(f"   系统消息: {system_messages}")
        print(f"   工具调用: {tool_messages}")
        print(f"   最大限制: {self.context_config['max_history_length']} 轮对话")

# 主程序
def main():
    # 创建对象
    client = Demo()

    # 系统提示词
    system_prompt = """
    请用中文回答。
    回答必须简洁直接，不做额外解释，不做科普，不补充未被要求的背景信息，不评价问题本身，不说明自身能力或限制。
    允许进行轻微的社交化回应。
    
    你可以使用以下工具：
    - get_time: 获取当前系统时间
    - calculate_math: 计算数学表达式
    - get_weather: 获取城市天气信息
    
    规则：
    1. 当用户询问时间、计算或天气时，必须使用相应工具。  
    2. 工具调用后，不要在同一条消息中输出任何解释性文字。  
    3. 工具返回内容会以 role="tool" 的纯文本形式出现。接收到工具结果后，你的下一条回复只需给出一句简短、直接的答案，不做扩展，不重复工具内容以外的信息。  
    4. 工具结果呈现必须为纯文本，不使用任何标签、XML、Markdown 代码块或其他包装格式。  
    5. 不要合并多个动作。如果需要计算和看时间，先调用 math，再调用 time，各自按流程回答。  
    6. 当用户直接给出某个时间（例如 “现在是 2025年12月3日10点20分”），将此视为普通输入，不讨论其是否真实或可能，也不额外补充说明。  
    7. 当用户要求执行不可能的操作（如读取本地文件）时，可以用自然、轻松的方式回应。
    8. 永远不要在回答中加入“作为 AI”“我不能”“实际上无法”等自述式句子，只需简短拒绝或简短回答。
    
    示例格式（必须遵守）：
    用户：现在几点？
    助手：（发起 get_time 工具调用）
    工具：2025-12-03 10:22:19
    助手：现在是 10:22:19
    
    用户：1+2 等于多少？
    助手：（发起 calculate_math 调用）
    工具：3
    助手：3
    
    用户：你能查看我电脑的文件吗？
    助手：无法查看本地文件。你可以把需要分析的内容粘贴出来。
    
    保持以上行为规范。
    """
    # 添加这一条提示词，以系统消息类型塞进上下文
    client.add_system_message(system_prompt)

    print("\n📝 可用命令:")
    print("  - 'clear': 清空对话历史")
    print("  - 'context': 显示上下文信息")
    print("  - 'system <消息>': 设置系统提示")
    print("  - 'quit/exit/退出': 退出程序")
    print("  - 'switch': 切换模型")

    # 显示初始上下文信息
    client.show_context_info()

    while True:
        user_input = input("\n👤 你: ").strip()

        if user_input.lower() in ['quit', 'exit', '退出']:
            break
        elif user_input.lower() == 'clear':
            keep_system = input("是否保留系统消息？(y/n, 默认y): ").strip().lower() != 'n'
            client.clear_context(keep_system_message=keep_system)
            continue
        elif user_input.lower() == 'context':
            client.show_context_info()
            continue
        elif user_input.lower().startswith('system '):
            new_system_msg = user_input[7:].strip()
            client.add_system_message(new_system_msg)
            print("✅ 系统提示已更新")
            continue
        elif user_input.lower() == 'switch':
            print(f"当前模型: {client.model}")
            new_model = input("输入新模型名称: ").strip()
            client.model = new_model
            print(f"✅ 已切换到: {client.model}")
            continue
        elif not user_input:
            continue

        # 开始聊天
        client.stream_chat(user_input)

if __name__ == "__main__":
    main()