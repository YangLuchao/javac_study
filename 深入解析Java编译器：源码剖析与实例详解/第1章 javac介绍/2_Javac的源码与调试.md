# Javac的源码与调试

解压后可以在openjdk/langtools/src/share/classes/com/sun/tools路径下找到javac目录，该目录下存放着Javac主要的源代码实现，可以将相关的源代码复制到IDE中，这样就可以借助Eclipse等IDE调试源代码了。首先在Eclipse中新建Java项目，名称为JavacCompiler，然后将openjdk/langtools/src/share/classes/路径下的com目录复制到项目的src目录下，最后的项目结构如图
![image](https://github.com/YangLuchao/img_host/raw/master/20230418/image.1oa5vix94rnk.webp)

![image](https://github.com/YangLuchao/img_host/raw/master/20230418/image.6igqtc2zmh00.webp)



## Javac主要目录说明

![image](https://github.com/YangLuchao/img_host/raw/master/20230418/image.5ut14gnzgog0.webp)

![image](https://github.com/YangLuchao/img_host/raw/master/20230418/image.68n28jvykk80.webp)
