# hackathon_tank
game engine of tank war for hackathon 2017

tank大战

比赛形式：
1. 比赛采取循环赛制，即每组队伍循环和其他队伍进行两两对战，单局比赛获胜方得3分，败者得0分，平局各得1分。最终获得分数高的队伍获胜。
2. 组委会提供5张对战地图。第一个星期一张，第二个星期一张，最后一天提供三张。最终单局比赛成绩取两只队伍在5张地图上的对战结果计算。然后按循环赛积分进行排名。
3. 前期的比赛只公布各团队的积分，对对战过程进行保密。

比赛细节描述
2. 地图大小为N*N（每张地图可能大小不一致），中心旋转对称，队伍的tank出生在地图的两个对边角上。
3. 地图上有以下元素（但是不保证所有的元素在每一张地图中都会出现）
    1. 障碍物，不可被摧毁。地图的最外围会被一圈障碍物覆盖。
    2. 战旗，始终固定在地图中心出现。可被经过的tank获得。战旗消失后会在固定回合后重新出现。
    3. 森林，处于森林中的tank不会被敌方发现。处于森里中的炮弹对双方均不可见。
4. 每只队伍M量tank。
5. 每辆tank在地图上最多只能存在一发炮弹。必须在之前发射的炮弹消失掉之后才能进行开火的操作。
6. tank可以进行如下操作：左转，右转，掉头，前进，原地不动，开火。开火方向可以和坦克的行进方向不一样。开火方向限制在上下左右四个方向。每个 回合只能执行一个操作。
7. tank速度为x，炮弹速度为y，其中x<y。
8. 炮弹之间不能互相抵消，炮弹不区分敌我。
9. tank有H格血量，每发炮弹只能打掉tank一格血量。
10. 炮弹具有最高优先结算权，即游戏首先结算炮弹的行进，然后才结算tank的操作。如果炮弹击毁一辆tank，此辆tank将不具有进行下一步操作的权利。炮弹命中tank或障碍物之后炮弹即消失。多发炮弹命中同一量坦克后，所有炮弹全部消失，即使被命中的坦克只有一滴HP。
11. 同一坐标不允许有两辆tank存在，即两辆tank的下一步操作导致进入相同坐标，这两辆tank的位置停留在进入相同坐标前的位置。例如，如果两辆坦克行进两格后会处于同于位置，则两辆坦克各自行进一格。
12. 单场地图比赛获胜条件：
	A）在限定的回合当中，一方的坦克被全部击毁则游戏结束，幸存的一方获胜。
	B）在限定的回合结束后，双方均有坦克存留，按以下方式结算：
    1. 每辆剩余的tank获得i分
    2. 每一面获取的战旗得j分
    3. i+j分数高的一方获胜

游戏引擎设计：
    1. 参赛选手的程序和判题程序通过thrift协议交互。
    2. 参赛选手的程序部署在独立的docekr上，并启动相应的thrift服务。
    3. 游戏初始，判题程序向参赛双方提供如下数据：地图，每辆坦克的属性
    4. 之后判题程序工作于询问模式，每回合询问参赛双方一次操作指令，然后对操作指令进行结算，并将结算结果返回参赛双方，并进入下一回合。
    5. 每回个超时时间为s毫秒，如果超时则视参赛方tank不进行任何操作。
    6. 比赛最多进行t回合。如果t回合之前一方tank全部被击毁，游戏结束。

为了保证比赛的公平性以及区分度，组委会保留对以上描述中的变量（n,m,x,y,h,i,j,t）进行临时调整的权利。大致范围如下
N<30,M<5, X<Y<=4, h<4, i=j, t<=200, s<=2000ms

