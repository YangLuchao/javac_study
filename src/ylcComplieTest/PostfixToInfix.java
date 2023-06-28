package ylcComplieTest;

import java.util.Stack;

public class PostfixToInfix {
    public static String postfixToInfix(String postfix) {
        Stack<String> stack = new Stack<>();
        for (int i = 0; i < postfix.length(); i++) {
            char c = postfix.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                stack.push(String.valueOf(c));
            } else {
                String operand2 = stack.pop();
                String operand1 = stack.pop();
                stack.push("(" + operand1 + c + operand2 + ")");
            }
        }
        return stack.pop();
    }

    public static void main(String[] args) {
        String postfix = "34+2*15-2/^";
        String infix = postfixToInfix(postfix);
        System.out.println(infix);
    }
}