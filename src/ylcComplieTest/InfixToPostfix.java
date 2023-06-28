package ylcComplieTest;

import java.util.Stack;

public class InfixToPostfix {
    public static String infixToPostfix(String infix) {
        StringBuilder postfix = new StringBuilder();
        Stack<Character> stack = new Stack<>();
        for (int i = 0; i < infix.length(); i++) {
            char c = infix.charAt(i);
            // 如果是数字或字母，直接加入后缀表达式
            if (Character.isLetterOrDigit(c)) {
                postfix.append(c);
            }
            // 如果是左括号，入栈
            else if (c == '(') {
                stack.push(c);
            }
            // 如果是右括号，弹出栈顶元素，直到找到左括号
            else if (c == ')') {
                while (!stack.isEmpty() && stack.peek() != '(') {
                    postfix.append(stack.pop());
                }
                if (!stack.isEmpty() && stack.peek() != '(') {
                    return "Invalid Expression";
                } else {
                    stack.pop();
                }
            }
            // 如果是运算符
            else {
                while (!stack.isEmpty() && precedence(c) <= precedence(stack.peek())) {
                    postfix.append(stack.pop());
                }
                stack.push(c);
            }
        }
        // 将栈中剩余的运算符弹出，加入后缀表达式
        while (!stack.isEmpty()) {
            postfix.append(stack.pop());
        }
        return postfix.toString();
    }
    
    // 优先级判定
    public static int precedence(char c) {
        switch (c) {
            case '+':
            case '-':
                return 1;
            case '*':
            case '/':
                return 2;
            case '^':
                return 0;
        }
        return -1;
    }

    public static void main(String[] args) {
        String infix = "(3+4)*2/(1-5)^2";
        String postfix = infixToPostfix(infix);
        System.out.println(postfix);
    }
}