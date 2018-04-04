package com.mmall.service.impl;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;


import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.mmall.common.Const;
import com.mmall.common.ResponseCode;
import com.mmall.common.ServerResponse;
import com.mmall.dao.CartMapper;
import com.mmall.dao.ProductMapper;
import com.mmall.pojo.Cart;
import com.mmall.pojo.Product;
import com.mmall.service.ICartService;
import com.mmall.util.BigDecimalUtil;
import com.mmall.util.PropertiesUtil;
import com.mmall.vo.CartProductVo;
import com.mmall.vo.CartVo;

@Service
public class CartServiceImpl implements ICartService
{
	@Autowired
	private CartMapper cartMapper;
	@Autowired
	private ProductMapper productMapper;
	
	/**
	 * @see 向购物车中添加商品
	 * @param userId
	 * @param productId
	 * @param count
	 * @return 业务对象CartVo,封装了此购物车所有商品的信息
	 */
	public ServerResponse<CartVo> add(Integer userId,Integer productId,Integer count)
	{
		if(userId==null||count==null)
		{
			return ServerResponse.createByErrorCodeMessage(ResponseCode.ILLEGAL_ARGUMENT.getCode(), ResponseCode.ILLEGAL_ARGUMENT.getDesc());
		}
		
		//1.根据userId和productId查询是否购物车中有这个商品,决定向购物车中新增商品还是更新商品的数量
		Cart cart=cartMapper.selectByUserIdAndProductId(userId,productId);
		if(cart==null)
		{
			//插入一条记录
			Cart newCart=new Cart();
			newCart.setUserId(userId);
			newCart.setProductId(productId);
			newCart.setQuantity(count);
			newCart.setChecked(Const.Cart.CHECKED);
			cartMapper.insert(newCart);
		}
		else
		{
			//更新已有购物车商品的的数量
			cart.setQuantity(count+cart.getQuantity());
			cartMapper.updateByPrimaryKeySelective(cart);
		}
		
		//跟新商品对应的状态,并返回购物车详情
		return this.list(userId);
		
	}
	
	/**
	 * @see 更新购物车中某个商品的数量
	 * @param userId
	 * @param productId
	 * @param count
	 * @return
	 */
	public ServerResponse<CartVo> update(Integer userId,Integer productId,Integer count)
	{
		if(productId==null||count==null)
		{
			return ServerResponse.createByErrorCodeMessage(ResponseCode.ILLEGAL_ARGUMENT.getCode(), ResponseCode.ILLEGAL_ARGUMENT.getDesc());
		}
		Cart cart=cartMapper.selectByUserIdAndProductId(userId, productId);
		if(cart!=null)
		{
			cart.setQuantity(count);
			cartMapper.updateByPrimaryKeySelective(cart);
		}
		return this.list(userId);
	}
	
	
	/**
	 * @see 删除选中的购物车中的商品
	 * @param userId
	 * @param productIds
	 * @return
	 */
	public ServerResponse<CartVo> delete(Integer userId,String productIds)
	{
		List<String> productList=Splitter.on(",").splitToList(productIds);
		if(productList.size()<1)
		{
			return ServerResponse.createByErrorCodeMessage(ResponseCode.ILLEGAL_ARGUMENT.getCode(), ResponseCode.ILLEGAL_ARGUMENT.getDesc());
		}
		cartMapper.deleteByUsrIdAndProductIds(userId, productList);
		return this.list(userId);
	}
	
	/**
	 * @see 全选购物车/全不选购物车/勾选某个商品/取消勾选某个商品
	 * @param userId
	 * @param productId
	 * @param checked
	 * @return
	 */
	public ServerResponse<CartVo> selectOrUnselect(Integer userId,Integer productId,Integer checked)
	{
		cartMapper.checkOruncheckedProduct(userId, productId, checked);
		return list(userId);
	}
	
	public ServerResponse<Integer> getCartProductCount(Integer userId)
	{
		if(userId==null)
		{
			return ServerResponse.createBySuccess(0);
		}
		return ServerResponse.createBySuccess(cartMapper.getCartProductCount(userId));
	}
	
	public ServerResponse<CartVo> list(Integer userId)
	{
		CartVo cartVo=this.getCartVoLimit(userId);
		return ServerResponse.createBySuccess(cartVo);
	}
	/**
	 * @see 此方法的作用是根据userId查询这个用户的购物车的所有商品信息,并对应跟新状态
	 * @param userId
	 * @return
	 */
	private CartVo getCartVoLimit(Integer userId)
	{
		CartVo cartVo=new CartVo();//将要返回的vo对象
		BigDecimal cartTotalPrice=new BigDecimal("0");//这个用户的购物车总价值
		List<CartProductVo> cartProductVoList=Lists.newArrayList();//用来存储购物车中的产品
		
		
		
		
		//1.根据userId查询这个用户购物车中的所有商品
		List<Cart> cartList=cartMapper.selectByUserId(userId);
		
		//2.遍历这个cartList,填充CartProductVo对象
		if(!CollectionUtils.isEmpty(cartList))
		{
			for(Cart cartItem:cartList)
			{
				CartProductVo cartProductVo=new CartProductVo();
				cartProductVo.setId(cartItem.getId());
				cartProductVo.setUserId(cartItem.getUserId());
				cartProductVo.setProductId(cartItem.getProductId());
				
				//3.根据productId查询出此商品的具体信息
				Product product=productMapper.selectByPrimaryKey(cartItem.getProductId());
				if(product!=null)
				{
					cartProductVo.setProductName(product.getName());
					cartProductVo.setProductSubtitle(product.getSubtitle());
					cartProductVo.setProductMainImage(product.getMainImage());
					cartProductVo.setProductPrice(product.getPrice());
					cartProductVo.setProductStatus(product.getStatus());
					cartProductVo.setProductStock(product.getStock());
					
					int buyLimitCount=0;
					if(product.getStock()>=cartItem.getQuantity())
					{
						//库存数量足够
						buyLimitCount=cartItem.getQuantity();
						cartProductVo.setLimitQuantity(Const.Cart.LIMIT_NUM_SUCCESS);
					}
					else
					{
						buyLimitCount=product.getStock();
						Cart cart=new Cart();
						cart.setId(cartItem.getId());
						cart.setQuantity(buyLimitCount);
						cartMapper.updateByPrimaryKeySelective(cart);
						cartProductVo.setLimitQuantity(Const.Cart.LIMIT_NUM_FAIL);
					}
					cartProductVo.setQuantity(buyLimitCount);
					cartProductVo.setProductTotalPrice(BigDecimalUtil.mul(product.getPrice().doubleValue(),cartProductVo.getQuantity().doubleValue()));
					
					//如果这个产品已选中,则要把价钱加到购物车总价值中
					if(cartItem.getChecked()==Const.Cart.CHECKED)
					{
						cartTotalPrice=BigDecimalUtil.add(cartTotalPrice.doubleValue(),cartProductVo.getProductTotalPrice().doubleValue());
					}
				}
				cartProductVoList.add(cartProductVo);	
			}
		}
		cartVo.setCartProductVoList(cartProductVoList);
		cartVo.setCartTotalPrice(cartTotalPrice);
		cartVo.setAllChecked(this.getAllCheckedStatus(userId));
		cartVo.setImageHost(PropertiesUtil.getProperty("ftp.server.http.prefix", "http://img.happymmall.com/"));
		
		return cartVo;
	}
	
	/**
	 * 判断某个购物车下产品是否被全选
	 * @return
	 */
	private boolean getAllCheckedStatus(Integer userId)
	{
		if(userId==null)
		{
			return false;
		}
		return cartMapper.selectCartCheckedStatusByUserId(userId)==0;
	}
}
