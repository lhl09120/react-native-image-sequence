//
// Created by Mads Lee Jensen on 07/07/16.
// Copyright (c) 2016 Facebook. All rights reserved.
//

#import "RCTImageSequenceView.h"

@implementation RCTImageSequenceView {
    NSUInteger _framesPerSecond;
    NSMutableDictionary *_activeTasks;
    NSMutableDictionary *_imagesLoaded;
    BOOL _loop;
    NSUInteger _interval;
    NSTimer *_timer;
}

- (void)setImages:(NSArray *)images {
    __weak RCTImageSequenceView *weakSelf = self;
    
    self.animationImages = nil;
    
    _activeTasks = [NSMutableDictionary new];
    _imagesLoaded = [NSMutableDictionary new];
    
    for (NSUInteger index = 0; index < images.count; index++) {
        NSDictionary *item = images[index];
        
        NSString *url = item[@"uri"];
        
        dispatch_async(dispatch_queue_create("dk.mads-lee.ImageSequence.Downloader", NULL), ^{
            UIImage *image;
            if (item[@"useXcassets"]) {
                image = [UIImage imageNamed:url];
            } else if (item[@"bundleName"]) {
                // 1. 找到特定bundle
                NSString *bundlePath = [[NSBundle mainBundle] pathForResource:item[@"bundleName"] ofType:nil];
                // 2. 载入bundle，即创建bundle对象
                NSBundle *bundle = [NSBundle bundleWithPath:bundlePath];
                // 3. 从bundle中获取资源路径
                NSString *picPath = [bundle pathForResource:url ofType:nil];
                // 4. 通过路径创建对象
                image = [UIImage imageWithContentsOfFile:picPath];
            } else {
                image = [UIImage imageWithData:[NSData dataWithContentsOfURL:[NSURL URLWithString:url]]];
            }
            dispatch_async(dispatch_get_main_queue(), ^{
                [weakSelf onImageLoadTaskAtIndex:index image:image];
            });
        });
        
        _activeTasks[@(index)] = url;
    }
}

- (void)onImageLoadTaskAtIndex:(NSUInteger)index image:(UIImage *)image {
    if (index == 0) {
        self.image = image;
    }
    
    [_activeTasks removeObjectForKey:@(index)];
    
    _imagesLoaded[@(index)] = image;
    
    if (_activeTasks.allValues.count == 0) {
        [self onImagesLoaded];
    }
}

- (void)animationDidFinish {
    NSUInteger lastIndex = [self.animationImages count] - 1;
    self.image = [self.animationImages objectAtIndex:lastIndex];
    self.animationImages = nil;
}

- (void)onImagesLoaded {
    NSMutableArray *images = [NSMutableArray new];
    for (NSUInteger index = 0; index < _imagesLoaded.allValues.count; index++) {
        UIImage *image = _imagesLoaded[@(index)];
        [images addObject:image];
    }
    
    [_imagesLoaded removeAllObjects];
    
    self.image = nil;
    self.animationDuration = images.count * (1.0f / _framesPerSecond);
    self.animationImages = images;
    [self setAnimationRepeatCountByIntervalAndLoop];
    if (!_loop) {
        [self performSelector:@selector(animationDidFinish)
                   withObject:nil
                   afterDelay:self.animationDuration];
    }
    [self playAnimation];
}

- (void)setFramesPerSecond:(NSUInteger)framesPerSecond {
    _framesPerSecond = framesPerSecond;
    
    if (self.animationImages.count > 0) {
        self.animationDuration = self.animationImages.count * (1.0f / _framesPerSecond);
    }
}
/**
 设置两次动画播放的间隔

 @param interval 间隔，单位：秒
 */
- (void)setInterval:(NSUInteger)interval {
    _interval = interval;
    [self setAnimationRepeatCountByIntervalAndLoop];
}

- (void)setLoop:(NSUInteger)loop {
    _loop = loop;
    [self setAnimationRepeatCountByIntervalAndLoop];
}

/**
 当设置了动画间隔以后，动画直接设置为不循环播放，通过定时任务控制动画播放
 */
- (void)setAnimationRepeatCountByIntervalAndLoop {
    if (_interval > 0) {
        self.animationRepeatCount = 1;
    } else {
        self.animationRepeatCount = _loop ? 0 : 1;
    }
}

/**
 当视图变得不可见时，如果存在定时任务，则将其取消。

 @param newWindow
 */
-(void)willMoveToWindow:(UIWindow *)newWindow {
    [super willMoveToWindow:newWindow];
    if (newWindow == nil && _timer != nil) {
        [_timer invalidate];
    }
}

/**
 播放动画，根据 interval 是否设定决定是否启用定时任务
 */
- (void)playAnimation {
    if (self.animationImages == nil) {
        return;
    }
    if (_interval > 0) {
        _timer = [NSTimer scheduledTimerWithTimeInterval:_interval target:self selector:@selector(playAnimation) userInfo:nil repeats:NO];
    }
    [self stopAnimating];
    [self startAnimating];
}
@end
