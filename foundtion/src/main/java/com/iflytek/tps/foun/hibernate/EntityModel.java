package com.iflytek.tps.foun.hibernate;


import com.iflytek.tps.foun.util.DateUtils;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;

/**
 * Created by losyn on 6/12/17.
 */
@DynamicInsert
@DynamicUpdate
@MappedSuperclass
@EntityListeners({EntityModel.DateEntityListener.class})
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
public abstract class EntityModel implements Serializable{
    private static final long serialVersionUID = -5244614184696053278L;

    /** 主键ID **/
    @Id
    @Column(name = "id", length = 32)
    @GeneratedValue(generator = "uuid")
    @GenericGenerator(name = "uuid", strategy = "uuid")
    public String id;

    /** 创建时间 **/
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "create_time", length = 7)
    public Date createTime;

    /** 更新时间 **/
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "modify_time", length = 7)
    public Date modifyTime;


    public static class DateEntityListener {
        /** 当持久化对象更新时，在更新前就会执行这个函数，用于自动更新修改日期字段 */
        @PreUpdate
        public void onPreUpdate(Object o) {
            if (o instanceof EntityModel) {
                Date now = DateUtils.now();
                EntityModel em = (EntityModel) o;
                em.modifyTime = now;
                if(null == em.createTime) {
                    em.createTime = now;
                }
            }
        }

        /** 当保存一个entity对象时，在保存之前会执行这个函数，用于自动添加创建日期 */
        @PrePersist
        public void onPrePersist(Object o) {
            if (o instanceof EntityModel) {
                Date currentDate = DateUtils.now();
                ((EntityModel) o).createTime = currentDate;
                ((EntityModel) o).modifyTime = currentDate;
            }
        }
    }
}
